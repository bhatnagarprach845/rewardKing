package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.dto.ExtractionResult;
import com.bhatn.cashbackking.dto.PayoutStatusResponse;
import com.bhatn.cashbackking.entity.*;
import com.bhatn.cashbackking.repository.CashbackTransactionRepository;
import com.bhatn.cashbackking.repository.ReceiptRepository;
import com.bhatn.cashbackking.repository.UserRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import com.bhatn.cashbackking.service.ReceiptProcessor;
import com.bhatn.cashbackking.service.UserService;
import com.bhatn.cashbackking.service.WalletService;
import com.bhatn.cashbackking.service.ocr.BillAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class ReceiptController {

    private final ReceiptProcessor receiptProcessor;
    private final ReceiptRepository receiptRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final BillAnalyzer billAnalyzer;
    private final UserService userService;
    private final WalletService walletService;
    private final String bucketName;

    // A single, explicit constructor handles both beans and value injections flawlessly
    public ReceiptController(
            ReceiptProcessor receiptProcessor,
            ReceiptRepository receiptRepository,
            WalletRepository walletRepository,
            UserRepository userRepository,
            CashbackTransactionRepository transactionRepository,
            BillAnalyzer billAnalyzer,
            UserService userService,
            WalletService walletService,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.receiptProcessor = receiptProcessor;
        this.receiptRepository = receiptRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.billAnalyzer = billAnalyzer;
        this.userService = userService;
        this.walletService = walletService;
        this.bucketName = bucketName;
    }

    /**
     * 1. S3 Processing (Production/Android App)
     */
    @PostMapping("/process-s3")
    public ResponseEntity<Map<String, Object>> processS3Receipt(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        String s3Key = request.get("s3Key");

        log.info("Processing S3 receipt for user: {}", userId);

        Receipt receipt = new Receipt();
        receipt.setUserId(userId);
        receipt.setS3Key(s3Key);
        receipt.setStatus(ReceiptStatus.PROCESSING);
        receipt = receiptRepository.save(receipt);

        receiptProcessor.processCashbackAsync(receipt.getId(), bucketName, s3Key);

        return ResponseEntity.ok(Map.of(
                "receiptId", receipt.getId(),
                "status", "PROCESSING_INITIATED"
        ));
    }

    /**
     * 2. Local Upload (Development/Web App)
     */
    @Transactional
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadLocal(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        log.info("User {} is uploading a receipt", userId);

        try {
            // Core Fix: Read directly from the standard input stream to bypass multipart metadata noise
            byte[] cleanImageBytes = file.getInputStream().readAllBytes();
            ExtractionResult result = billAnalyzer.analyze(cleanImageBytes);

            Receipt receipt = new Receipt();
            receipt.setUserId(userId);
            receipt.setTotalAmount(result.getTotalAmount());
            receipt.setMerchantName(result.getMerchantName());
            receipt.setPurchaseDate(result.getPurchaseDate());
            receipt.setStatus(ReceiptStatus.UPLOADED);
            receipt.setS3Key("local/" + UUID.randomUUID());

            if (result.getLineItems() != null) {
                List<ReceiptItem> entityItems = result.getLineItems().stream().map(dto -> {
                    ReceiptItem item = new ReceiptItem();
                    item.setDescription(dto.getDescription());
                    item.setQuantity(dto.getQuantity());
                    item.setTotalPrice(dto.getPrice());
                    item.setUnitPrice(dto.getUnitPrice());
                    item.setReceipt(receipt);
                    return item;
                }).collect(Collectors.toList());

                receipt.setItems(entityItems);
            }

            receiptRepository.save(receipt);
            receiptProcessor.processCashback(receipt);

            return ResponseEntity.ok(Map.of(
                    "status", receipt.getStatus(),
                    "merchant", result.getMerchantName(),
                    "extractedTotal", result.getTotalAmount()
            ));
        } catch (Exception e) {
            log.error("Error processing local upload", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "OCR extraction failed"));
        }
    }

    @GetMapping("/version") // Cleaned up path context redundancy
    public String version() {
        return "v2-bean-injection-fixed";
    }

    @PostMapping("/syncProfile")
    public ResponseEntity<User> syncProfile(
            @RequestBody Map<String, String> profileData,
            @AuthenticationPrincipal Jwt jwt) {

        String cognitoId = jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");

        User user = userRepository.findById(cognitoId)
                .orElse(User.builder().cognitoId(cognitoId).build());

        user.setEmail(email);
        user.setName(profileData.get("name"));
        user.setUpiId(profileData.get("upiId"));

        return ResponseEntity.ok(userRepository.save(user));
    }

    /**
     * 3. Payout Status (Real-time Dashboard Data)
     */
    @GetMapping("/payout-status")
    public ResponseEntity<PayoutStatusResponse> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");

        if (userId == null) {
            userId = jwt.getClaimAsString("username");
        }

        if (userId == null) {
            log.error("JWT claims missing primary identity keys: {}", jwt.getClaims());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findById(userId).orElse(null);
        String finalUserId = userId;
        UserWallet wallet = walletRepository.findById(userId)
                .orElseGet(() -> UserWallet.builder()
                        .userId(finalUserId)
                        .currentBalance(BigDecimal.ZERO)
                        .build());

        List<CashbackTransaction> history = transactionRepository.findByUserIdOrderByProcessedAtDesc(userId);

        BigDecimal threshold = new BigDecimal("30.00");
        BigDecimal needed = threshold.subtract(wallet.getCurrentBalance()).max(BigDecimal.ZERO);

        PayoutStatusResponse response = PayoutStatusResponse.builder()
                .currentBalance(wallet.getCurrentBalance())
                .threshold(threshold)
                .statusMessage(wallet.getCurrentBalance().compareTo(threshold) >= 0
                        ? "You are eligible for payout!"
                        : "₹" + needed + " more needed for payout")
                .upiId(user != null ? user.getUpiId() : null)
                .recentTransactions(history)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 4. Redeem Method
     */
    @Transactional
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, String>> redeem(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        BigDecimal minThreshold = new BigDecimal("30.00");

        try {
            UserWallet wallet = walletRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Wallet not found"));

            BigDecimal amountToRedeem = request.get("amount") != null
                    ? new BigDecimal(request.get("amount").toString())
                    : wallet.getCurrentBalance();

            if (amountToRedeem.compareTo(minThreshold) < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "error", "Minimum redemption amount is ₹30. Current request: ₹" + amountToRedeem
                ));
            }

            if (wallet.getCurrentBalance().compareTo(amountToRedeem) < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Insufficient balance"));
            }

            walletService.redeemCashback(userId, amountToRedeem);
            return ResponseEntity.ok(Map.of("message", "Success! ₹" + amountToRedeem + " initiated."));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}