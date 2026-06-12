package com.bhatn.cashbackking.service;

import com.bhatn.cashbackking.dto.ExtractionResult;
import com.bhatn.cashbackking.entity.*;
import com.bhatn.cashbackking.repository.*;
import com.bhatn.cashbackking.service.ocr.BillAnalyzer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessor {

    private final ReceiptRepository receiptRepository;
    private final WalletRepository walletRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final BillAnalyzer billAnalyzer;
    private final S3Client s3Client;

    /**
     * FIX (High): Cashback rate is now configurable via application.properties.
     * Previously hardcoded to 1 (100%) with a "testing purpose" comment.
     * Default is 0.03 (3%) — override with cashback.rate=0.05 etc.
     */
    @Value("${cashback.rate:0.03}")
    private BigDecimal cashbackRate;

    @Async
    @Transactional
    public void processCashbackAsync(Long receiptId, String bucket, String key) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        try {
            byte[] imageBytes = downloadFromS3(bucket, key);

            // FIX (High): EXIF check now flags for review instead of outright rejecting.
            // Legitimate receipts from banking apps, email screenshots, or PDF-to-image
            // conversions have no camera EXIF and were previously silently rejected.
            // They are now queued for manual review instead.
            if (!isAuthenticCapture(imageBytes)) {
                log.warn("EXIF data absent or incomplete for receipt {}. Flagging for manual review.", receiptId);
                receipt.setStatus(ReceiptStatus.FLAGGED_FOR_REVIEW);
                receipt.setItems(new java.util.ArrayList<>());
                receiptRepository.save(receipt);
                return;
            }

            ExtractionResult result = billAnalyzer.analyze(imageBytes);

            receipt.setMerchantName(result.getMerchantName().toUpperCase().trim());
            receipt.setTotalAmount(result.getTotalAmount());
            receipt.setPurchaseDate(result.getPurchaseDate());

            if (result.getLineItems() != null) {
                if (receipt.getItems() == null) {
                    receipt.setItems(new java.util.ArrayList<>());
                }
                result.getLineItems().forEach(dto -> {
                    ReceiptItem item = new ReceiptItem();
                    item.setDescription(dto.getDescription());
                    item.setUnitPrice(dto.getPrice());
                    item.setQuantity(dto.getQuantity());
                    item.setReceipt(receipt);
                    receipt.getItems().add(item);
                });
            }

            if (!verifyReceiptMath(receipt)) {
                rejectReceipt(receipt, "FRAUD_ALERT: Total amount mismatched during line-item mathematical validation.");
                return;
            }

            processCashback(receipt);

        } catch (Exception e) {
            log.error("Async failure for receipt {}: {}", receiptId, e.getMessage());
            receipt.setStatus(ReceiptStatus.FAILED);
            receiptRepository.save(receipt);
        }
    }

    @Transactional
    public void processCashback(Receipt receipt) {
        String currentFingerprint = calculateFingerprintHash(receipt);
        receipt.setFingerprintHash(currentFingerprint);

        List<Receipt> matchingReceipts = receiptRepository.findByFingerprintHash(currentFingerprint);

        Optional<Receipt> trueDuplicateOpt = matchingReceipts.stream()
                .filter(existing -> !existing.getId().equals(receipt.getId()))
                .findFirst();

        if (trueDuplicateOpt.isPresent()) {
            Receipt existingReceipt = trueDuplicateOpt.get();

            if (!existingReceipt.getUserId().equals(receipt.getUserId())) {
                log.warn("CRITICAL FRAUD ALERT: User {} uploaded a receipt identical to User {}",
                        receipt.getUserId(), existingReceipt.getUserId());
                receipt.setStatus(ReceiptStatus.FLAGGED_FOR_REVIEW);
                receipt.setItems(new java.util.ArrayList<>());
                receiptRepository.save(receipt);
                return;
            } else {
                rejectReceipt(receipt, "FRAUD_ALERT: Identity collision. Bill has already been processed by this account.");
                return;
            }
        }

        if (isDuplicate(receipt)) {
            rejectReceipt(receipt, "FRAUD_ALERT: Deep item matches indicate duplicated receipt content profiles.");
            return;
        }

        // FIX (High): Use the injected cashbackRate (default 3%) instead of the
        // hardcoded BigDecimal("1") that was awarding 100% of the receipt total.
        BigDecimal cashbackAmount = receipt.getTotalAmount()
                .multiply(cashbackRate)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Awarding cashback of ₹{} ({}% of ₹{}) for receipt {}",
                cashbackAmount,
                cashbackRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),
                receipt.getTotalAmount(),
                receipt.getId());

        UserWallet wallet = walletRepository.findByUserIdForUpdate(receipt.getUserId())
                .orElseGet(() -> UserWallet.builder().userId(receipt.getUserId()).build());

        wallet.addBalance(cashbackAmount);
        walletRepository.save(wallet);

        CashbackTransaction tx = CashbackTransaction.builder()
                .receiptId(receipt.getId())
                .userId(receipt.getUserId())
                .amountAwarded(cashbackAmount)
                .processedAt(LocalDateTime.now())
                .status(CashbackTransaction.TransactionStatus.COMPLETED)
                .remarks(cashbackRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
                        + "% reward: " + receipt.getMerchantName())
                .build();
        transactionRepository.save(tx);

        receipt.setStatus(ReceiptStatus.PROCESSED);
        receiptRepository.save(receipt);
    }

    private String calculateFingerprintHash(Receipt receipt) {
        try {
            String compositeRawString = String.format("%s_%s_%s",
                    receipt.getMerchantName(),
                    receipt.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    receipt.getPurchaseDate() != null ? receipt.getPurchaseDate().toString() : "NODATE");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(compositeRawString.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("SHA-256 calculation exception: ", e);
            return "ERROR_" + System.currentTimeMillis();
        }
    }

    private void rejectReceipt(Receipt receipt, String reason) {
        log.warn("{} for User: {}", reason, receipt.getUserId());
        receipt.setStatus(ReceiptStatus.REJECTED);
        receipt.setItems(new java.util.ArrayList<>());
        receiptRepository.save(receipt);
    }

    private boolean isAuthenticCapture(byte[] imageBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(bais);
            ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (exifDir == null) {
                log.warn("EXIF directory absent. Possible web screenshot or AI render.");
                return false;
            }

            String cameraMake = exifDir.getString(ExifIFD0Directory.TAG_MAKE);
            String cameraModel = exifDir.getString(ExifIFD0Directory.TAG_MODEL);

            if (cameraMake == null || cameraModel == null) {
                log.warn("Camera hardware identity elements null. EXIF metadata incomplete.");
                return false;
            }

            log.info("EXIF verification success. Capture device: {} {}", cameraMake, cameraModel);
            return true;
        } catch (Exception e) {
            // If we can't read EXIF at all (e.g. PNG, BMP), treat as unverifiable rather than auto-reject.
            log.warn("Failed to inspect EXIF metadata. Treating as unverifiable.");
            return false;
        }
    }

    private boolean verifyReceiptMath(Receipt receipt) {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            return true;
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (ReceiptItem item : receipt.getItems()) {
            BigDecimal qty = new BigDecimal(item.getQuantity() != null ? item.getQuantity() : 1);
            BigDecimal itemTotal = item.getUnitPrice().multiply(qty);
            calculatedTotal = calculatedTotal.add(itemTotal);
        }

        BigDecimal variance = receipt.getTotalAmount().subtract(calculatedTotal).abs();
        boolean mathIsValid = variance.compareTo(new BigDecimal("5.00")) <= 0;

        if (!mathIsValid) {
            log.error("Math verification failed. Invoice total ₹{}, computed total ₹{}",
                    receipt.getTotalAmount(), calculatedTotal);
        }
        return mathIsValid;
    }

    private boolean isDuplicate(Receipt receipt) {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            log.info("No line items found. Relying on hash uniqueness.");
            return false;
        }

        int matchedItemsCount = 0;
        for (ReceiptItem newItem : receipt.getItems()) {
            boolean itemWasSeenBefore = receiptRepository.existsByDeepCheck(
                    receipt.getId(),
                    receipt.getMerchantName(),
                    receipt.getTotalAmount(),
                    receipt.getPurchaseDate(),
                    newItem.getDescription(),
                    newItem.getUnitPrice()
            );
            if (itemWasSeenBefore) {
                matchedItemsCount++;
            }
        }

        double duplicateThresholdRatio = (double) matchedItemsCount / receipt.getItems().size();
        log.info("Line items match density: {}%", duplicateThresholdRatio * 100);

        return duplicateThresholdRatio >= 0.75;
    }

    private byte[] downloadFromS3(String bucket, String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
            return objectBytes.asByteArray();
        } catch (S3Exception e) {
            throw new RuntimeException("Could not download receipt from S3", e);
        }
    }
}
