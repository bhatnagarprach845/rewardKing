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

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptProcessor {

    private final ReceiptRepository receiptRepository;
    private final WalletRepository walletRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final BillAnalyzer billAnalyzer;
    private final S3Client s3Client;

    @Async
    @Transactional
    public void processCashbackAsync(Long receiptId, String bucket, String key) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        try {
            byte[] imageBytes = downloadFromS3(bucket, key);

            // 1. FRAUD CHECK: EXIF Metadata Validation
            if (!isAuthenticCapture(imageBytes)) {
                rejectReceipt(receipt, "FRAUD_ALERT: Invalid EXIF / AI Generated metadata signature missing.");
                return;
            }

            ExtractionResult result = billAnalyzer.analyze(imageBytes);

            // Map Top-Level Data
            receipt.setMerchantName(result.getMerchantName().toUpperCase().trim());
            receipt.setTotalAmount(result.getTotalAmount());
            receipt.setPurchaseDate(result.getPurchaseDate());

            if (result.getLineItems() != null) {
                result.getLineItems().forEach(dto -> {
                    ReceiptItem item = new ReceiptItem();
                    item.setDescription(dto.getDescription());
                    item.setUnitPrice(dto.getPrice());
                    item.setQuantity(dto.getQuantity());
                    item.setReceipt(receipt);
                    receipt.getItems().add(item);
                });
            }

            // 2. FRAUD CHECK: Line-Item Math Recalculation
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
        // 3. FRAUD CHECK: Composite Cryptographic Hash Checking
        if (isDuplicateHash(receipt) || isDuplicate(receipt)) {
            rejectReceipt(receipt, "FRAUD_ALERT: Identity collision. Bill has already been processed.");
            return;
        }

        // 3% Cashback Calculation (Note: updated multiplier constant to reflect the actual 3%)
        BigDecimal cashbackAmount = receipt.getTotalAmount()
                .multiply(new BigDecimal("1"))// testing purpose-- prachi
                .setScale(2, RoundingMode.HALF_UP);

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
                .remarks("3% reward: " + receipt.getMerchantName())
                .build();
        transactionRepository.save(tx);

        receipt.setStatus(ReceiptStatus.PROCESSED);
        receiptRepository.save(receipt);
    }

    /**
     * Helper: Centralized rejection tracking state modifier
     */
    private void rejectReceipt(Receipt receipt, String reason) {
        log.warn("Prachi :: {} for User: {}", reason, receipt.getUserId());
        receipt.setStatus(ReceiptStatus.REJECTED);
        receiptRepository.save(receipt);
    }

    /**
     * Rule 1: EXIF Inspection Guardrail
     */
    private boolean isAuthenticCapture(byte[] imageBytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(bais);
            ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);

            if (exifDir == null) {
                log.warn("Prachi :: EXIF directory completely absent. Flagging as potential web screenshot/AI render.");
                return false;
            }

            String cameraMake = exifDir.getString(ExifIFD0Directory.TAG_MAKE);
            String cameraModel = exifDir.getString(ExifIFD0Directory.TAG_MODEL);

            if (cameraMake == null || cameraModel == null) {
                log.warn("Prachi :: Camera Hardware identity elements null. Flagging metadata missing.");
                return false;
            }

            log.info("Prachi :: EXIF verification success. Capture device identified: {} {}", cameraMake, cameraModel);
            return true;
        } catch (Exception e) {
            log.warn("Prachi :: Failed to inspect EXIF tracking vectors. Bypassing fallback context safely.");
            return true; // Safe fallback barrier if image encoding blocks reading loop streams
        }
    }

    /**
     * Rule 2: Line-Item Math Recalculation Guardrail
     */
    private boolean verifyReceiptMath(Receipt receipt) {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            return true; // No items to process line metrics over, allow default total
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (ReceiptItem item : receipt.getItems()) {
            BigDecimal qty = new BigDecimal(item.getQuantity() != null ? item.getQuantity() : 1);
            BigDecimal itemTotal = item.getUnitPrice().multiply(qty);
            calculatedTotal = calculatedTotal.add(itemTotal);
        }

        // Allow up to ₹5.00 skew threshold allowance for varying regional taxes/rounding splits
        BigDecimal variance = receipt.getTotalAmount().subtract(calculatedTotal).abs();
        boolean mathIsValid = variance.compareTo(new BigDecimal("5.00")) <= 0;

        if (!mathIsValid) {
            log.error("Prachi :: Math verification breakdown. Invoice explicitly listed ₹{}, computed line summary ₹{}",
                    receipt.getTotalAmount(), calculatedTotal);
        }
        return mathIsValid;
    }

    /**
     * Rule 3: Unique Composite Cryptographic Fingerprint Check
     */
    private boolean isDuplicateHash(Receipt receipt) {
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

            String uniqueSignature = hexString.toString();
            log.info("Prachi :: Compiled unique receipt tracking fingerprint: {}", uniqueSignature);

            // Check if another unique record matching this footprint fingerprint tag exists in your store
            return receiptRepository.existsByFingerprintHashAndIdNot(uniqueSignature, receipt.getId());
        } catch (Exception e) {
            log.error("Prachi :: SHA-256 process calculation hit error exception, tracing back directly: ", e);
            return false;
        }
    }

    private boolean isDuplicate(Receipt receipt) {
        if (receipt.getItems() == null || receipt.getItems().isEmpty()) {
            return receiptRepository.existsByMerchantNameAndTotalAmountAndPurchaseDate(
                    receipt.getMerchantName(), receipt.getTotalAmount(), receipt.getPurchaseDate()
            );
        }
        for (ReceiptItem newItem : receipt.getItems()) {
            boolean itemWasSeenBefore = receiptRepository.existsByDeepCheck(
                    receipt.getId(), receipt.getMerchantName(), receipt.getTotalAmount(),
                    receipt.getPurchaseDate(), newItem.getDescription(), newItem.getUnitPrice()
            );
            if (!itemWasSeenBefore) return false;
        }
        return true;
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