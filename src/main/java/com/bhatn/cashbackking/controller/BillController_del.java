package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.dto.ExtractionResult;
import com.bhatn.cashbackking.dto.PayoutStatusResponse;
import com.bhatn.cashbackking.entity.Receipt;
import com.bhatn.cashbackking.entity.ReceiptStatus;
import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.ReceiptRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import com.bhatn.cashbackking.service.ReceiptProcessor;
import com.bhatn.cashbackking.service.ocr.BillAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
//@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/bills")
public class BillController_del {

    @Autowired
    private BillAnalyzer billAnalyzer;

    @Autowired
    private ReceiptProcessor receiptProcessor;

    @Autowired
    private WalletRepository walletRepo;

    @Autowired
    private ReceiptRepository receiptRepo;

    /**
     * S3 Processing (Android App)
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processS3Bill(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = (jwt != null) ? jwt.getClaimAsString("sub") : "dev-user";
        String s3Key = request.get("s3Key");
        String bucket = "cashback-king-receipts"; // Match your S3 config

        log.info("Processing S3 file: {} for user: {}", s3Key, userId);

        // Initialize record for tracking
        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setS3Key(s3Key);
        receipt.setStatus(ReceiptStatus.UPLOADED);
        receipt = receiptRepo.save(receipt);

        // Delegate to Async Processor
        receiptProcessor.processCashbackAsync(receipt.getId(), bucket, s3Key);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Processing initiated",
                "receiptId", receipt.getId().toString()
        ));
    }

    /**
     * Local Upload (React/Dev)
     */
    @Transactional
    @PostMapping("/upload-local")
    public ResponseEntity<Map<String, Object>> uploadLocal(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = (jwt != null) ? jwt.getClaimAsString("sub") : "dev-user";

        try {
            // 1. Analyze using DTO
            ExtractionResult result = billAnalyzer.analyze(file.getBytes());

            // 2. Map Entity
            Receipt receipt = new Receipt();
            receipt.setUserId(userId);
            receipt.setMerchantName(result.getMerchantName());
            receipt.setTotalAmount(result.getTotalAmount());
            receipt.setPurchaseDate(result.getPurchaseDate());
            receipt.setS3Key("local/" + UUID.randomUUID());
            receipt.setStatus(ReceiptStatus.UPLOADED);

            // 3. Save & Process (Calling the service, not the repo!)
            receipt = receiptRepo.save(receipt);
            receiptProcessor.processCashback(receipt);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "extractedData", result,
                    "receiptId", receipt.getId()
            ));

        } catch (Exception e) {
            log.error("OCR extraction failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Processing failed"));
        }
    }

    @Transactional(readOnly = true)
    @GetMapping("/payout-status")
    public ResponseEntity<PayoutStatusResponse> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {
        String userId = (jwt != null) ? jwt.getClaimAsString("sub") : "dev-user";

        UserWallet wallet = walletRepo.findById(userId)
                .orElseGet(() -> UserWallet.builder()
                        .userId(userId)
                        .currentBalance(BigDecimal.ZERO)
                        .build());

        BigDecimal threshold = new BigDecimal("30.00");
        BigDecimal needed = threshold.subtract(wallet.getCurrentBalance()).max(BigDecimal.ZERO);

        PayoutStatusResponse response = PayoutStatusResponse.builder()
                .currentBalance(wallet.getCurrentBalance())
                .threshold(threshold)
                .statusMessage("₹" + needed + " more needed for payout")
                .recentTransactions(Collections.emptyList()) // Fetch from TxnRepo for real data
                .build();

        return ResponseEntity.ok(response);
    }
}