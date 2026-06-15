package com.bhatn.rewardking.service;

import com.bhatn.rewardking.dto.ExtractionResult;
import com.bhatn.rewardking.entity.*;
import com.bhatn.rewardking.repository.ReceiptRepository;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import com.bhatn.rewardking.service.ocr.BillAnalyzer;
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
    private final RewardTransactionRepository transactionRepository;
    private final BillAnalyzer billAnalyzer;
    private final S3Client s3Client;

    // Self-inject proxy to ensure internal invocations respect @Transactional boundaries
    private final ReceiptProcessor self;

    @Value("${Reward.rate:0.03}")
    private BigDecimal RewardRate;

    /**
     * Orchestrates network/OCR operations asynchronously.
     * REMOVED @Transactional: Keeps S3 and OCR processing outside DB connection lifecycle.
     */
    @Async
    public void processRewardAsync(Long receiptId, String bucket, String key) {
        // Fetch initially to verify existence; uses a short-lived read transaction via repo
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        try {
            byte[] imageBytes = downloadFromS3(bucket, key);

            if (!isAuthenticCapture(imageBytes)) {
                log.warn("EXIF data absent or incomplete for receipt {}. Flagging for manual review.", receiptId);
                self.updateReceiptStatus(receiptId, ReceiptStatus.FLAGGED_FOR_REVIEW);
                return;
            }

            ExtractionResult result = billAnalyzer.analyze(imageBytes);

            // Guard against total failure of OCR extraction to prevent downstream NPEs
            if (result == null || result.getTotalAmount() == null || result.getMerchantName() == null) {
                log.warn("OCR Parsing failed completely for receipt {}. Flagging for review.", receiptId);
                self.updateReceiptStatus(receiptId, ReceiptStatus.FLAGGED_FOR_REVIEW);
                return;
            }

            // Delegate state changes and rewards logic to an isolated write transaction via proxy
            self.executeRewardAllocation(receiptId, result);

        } catch (Exception e) {
            log.error("Async failure for receipt {}: {}", receiptId, e.getMessage(), e);
            try {
                self.updateReceiptStatus(receiptId, ReceiptStatus.FAILED);
            } catch (Exception ex) {
                log.error("Failed to mark receipt status as FAILED in DB: {}", ex.getMessage());
            }
        }
    }

    /**
     * Isolated transactional write block for applying OCR results and processing rewards.
     */
    @Transactional
    public void executeRewardAllocation(Long receiptId, ExtractionResult result) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt missing during processing stage"));

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

        processReward(receipt);
    }

    /**
     * Fallback utility to safely save execution status transitions on failure.
     */
    @Transactional
    public void updateReceiptStatus(Long receiptId, ReceiptStatus status) {
        receiptRepository.findById(receiptId).ifPresent(receipt -> {
            receipt.setStatus(status);
            receipt.setItems(new java.util.ArrayList<>());
            receiptRepository.save(receipt);
        });
    }

    // Unmodified core business rule methods (processReward, isDuplicate, etc.) kept intact
    @Transactional
    public void processReward(Receipt receipt) {
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

        BigDecimal RewardAmount = receipt.getTotalAmount()
                .multiply(RewardRate)
                .setScale(2, BigDecimal.ROUND_HALF_UP);

        log.info("Awarding Reward of ₹{} ({}% of ₹{}) for receipt {}",
                RewardAmount,
                RewardRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString(),
                receipt.getTotalAmount(),
                receipt.getId());

        UserWallet wallet = walletRepository.findByUserIdForUpdate(receipt.getUserId())
                .orElseGet(() -> UserWallet.builder().userId(receipt.getUserId()).build());

        wallet.addBalance(RewardAmount);
        walletRepository.save(wallet);

        RewardTransaction tx = RewardTransaction.builder()
                .receiptId(receipt.getId())
                .userId(receipt.getUserId())
                .amountAwarded(RewardAmount)
                .processedAt(LocalDateTime.now())
                .status(RewardTransaction.TransactionStatus.COMPLETED)
                .remarks(RewardRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
                        + "% reward: " + receipt.getMerchantName())
                .build();
        transactionRepository.save(tx);

        receipt.setStatus(ReceiptStatus.PROCESSED);
        receiptRepository.save(receipt);
    }

    private String calculateFingerprintHash(Receipt receipt) {
        try {
            // Defend against null components to prevent a NullPointerException
            String merchant = receipt.getMerchantName() != null ? receipt.getMerchantName() : "UNKNOWN";
            String amount = receipt.getTotalAmount() != null
                    ? receipt.getTotalAmount().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString()
                    : "0.00";
            String date = receipt.getPurchaseDate() != null ? receipt.getPurchaseDate().toString() : "NODATE";

            String compositeRawString = String.format("%s_%s_%s", merchant, amount, date);

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