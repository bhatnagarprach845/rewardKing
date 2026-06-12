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
import com.bhatn.cashbackking.service.payment_del.PayoutService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
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
    private final PayoutService payoutService;
    private final String bucketName;

    public ReceiptController(
            ReceiptProcessor receiptProcessor,
            ReceiptRepository receiptRepository,
            WalletRepository walletRepository,
            UserRepository userRepository,
            CashbackTransactionRepository transactionRepository,
            BillAnalyzer billAnalyzer,
            UserService userService,
            WalletService walletService,
            PayoutService payoutService,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.receiptProcessor = receiptProcessor;
        this.receiptRepository = receiptRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.billAnalyzer = billAnalyzer;
        this.userService = userService;
        this.walletService = walletService;
        this.payoutService = payoutService;
        this.bucketName = bucketName;
    }

    /**
     * 1. S3 Processing - Safely isolated from background context race loops
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

        // Force immediate write-through verification using saveAndFlush
        receipt = receiptRepository.saveAndFlush(receipt);

        // Handoff key identifiers cleanly to prevent lazy-loading context drop anomalies
        receiptProcessor.processCashbackAsync(receipt.getId(), bucketName, s3Key);

        return ResponseEntity.ok(Map.of(
                "receiptId", receipt.getId(),
                "status", "PROCESSING_INITIATED"
        ));
    }

    /**
     * 2. Local Upload (Development)
     */
    @Transactional
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadLocal(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        log.info("User {} is uploading a receipt", userId);

        try {
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

    /**
     * New Link Verification Action: Matches frontend handleAddNewUpiSubmit()
     */
    @PostMapping("/addUpiId")
    public ResponseEntity<?> addUpiId(
            @RequestBody Map<String, String> requestBody,
            @AuthenticationPrincipal Jwt jwt) {

        String cognitoId = jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        String targetUpi = requestBody.get("upiId");

        if (targetUpi == null || targetUpi.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "UPI ID string is required."));
        }

        // ========================================================
        // 🛑 ENFORCED FRONT-LINE SANDBOX ERROR STATE INTERCEPTOR
        // ========================================================
        String checkedUpi = targetUpi.trim().toLowerCase();
        if (checkedUpi.equals("fail@razorpay") || checkedUpi.contains("invalid")) {
            log.warn("Front-line Sandbox Guard triggered for input: {}", targetUpi);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Payout blocked (Sandbox Mock): The UPI ID '" + targetUpi + "' is simulated as unauthentic."));
        }

        if (!targetUpi.matches("^[\\w.-]+@[\\w.-]+$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid UPI ID format. Structure must match example@bank."));
        }
        // ========================================================

        String customerName = requestBody.get("name");
        if (customerName == null || customerName.isBlank()) {
            customerName = jwt.getClaimAsString("name");
        }
        if (customerName == null || customerName.isBlank()) {
            customerName = "Valued Member";
        }

        log.info("Registering UPI ID request for User: {}, Name: {}, Target: {}", cognitoId, customerName, targetUpi);

        try {
            User user = userRepository.findById(cognitoId)
                    .orElseGet(() -> User.builder()
                            .cognitoId(cognitoId)
                            .email(email)
                            .name(requestBody.getOrDefault("name", "Valued Member"))
                            .build());

            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(customerName);
            }

            user.setUpiId(targetUpi);
            payoutService.getOrCreateFundAccountId(user);

            if (user.getUpiIds() == null) {
                user.setUpiIds(new ArrayList<>());
            }
            if (!user.getUpiIds().contains(targetUpi)) {
                user.getUpiIds().add(targetUpi);
            }

            user.setSelectedUpi(targetUpi);
            User savedUser = userRepository.save(user);
            log.info("UPI ID successfully validated and written to DB for user profile row: {}", savedUser.getCognitoId());

            return ResponseEntity.ok(Map.of("message", "UPI ID verified and linked successfully!"));

        } catch (IllegalArgumentException e) {
            log.warn("VPA validation rejected by structural validation layer logic: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("CRITICAL EXCEPTION inside /addUpiId processing loop:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Validation processing fault. Detail: " + e.getMessage()));
        }
    }

    /**
     * 3. Payout Status (Real-time Dashboard Data) - DTO Map Layer Configured
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

        // Pull raw database records
        List<CashbackTransaction> history = transactionRepository.findByUserIdOrderByProcessedAtDesc(userId);

        // Convert the database entries cleanly into the frontend's TransactionDTO structures
        List<PayoutStatusResponse.TransactionDTO> mappedHistory = history.stream().map(tx ->
                PayoutStatusResponse.TransactionDTO.builder()
                        .id(tx.getId())
                        .amount(tx.getAmountAwarded())
                        .type(tx.getAmountAwarded().compareTo(BigDecimal.ZERO) >= 0 ? "CREDIT" : "DEBIT")
                        .status(tx.getStatus() != null ? tx.getStatus().toString() : "COMPLETED")
                        .date(tx.getProcessedAt() != null ? tx.getProcessedAt().toLocalDate().toString() : "")
                        .build()
        ).collect(Collectors.toList());

        BigDecimal threshold = new BigDecimal("30.00");
        BigDecimal needed = threshold.subtract(wallet.getCurrentBalance()).max(BigDecimal.ZERO);

        PayoutStatusResponse response = PayoutStatusResponse.builder()
                .name(user != null ? user.getName() : "Valued Member")
                .email(user != null ? user.getEmail() : "")
                .currentBalance(wallet.getCurrentBalance())
                .threshold(threshold)
                .statusMessage(wallet.getCurrentBalance().compareTo(threshold) >= 0
                        ? "You are eligible for payout!"
                        : "₹" + needed + " more needed for payout")
                .upiId(user != null ? user.getUpiId() : null)
                .upiIds(user != null ? user.getUpiIds() : List.of())
                .selectedUpi(user != null ? user.getSelectedUpi() : null)
                .recentTransactions(mappedHistory) // Supplies the mapped DTO list
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 4. Redeem Method - Picks up selected radio routing target destination
     */
    @Transactional
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, String>> redeem(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        BigDecimal minThreshold = new BigDecimal("30.00");

        String explicitTargetUpi = request.get("targetUpi") != null ? request.get("targetUpi").toString() : null;
        log.info("Processing redemption lock request for user: {} to target: {}", userId, explicitTargetUpi);

        // 🔒 RULE 1: BLOCK CONCURRENT REQUESTS IF A PAYOUT IS ALREADY PENDING
        // Assuming your Status Enum is TransactionStatus.PENDING (or use the String "PENDING")
        boolean hasPending = transactionRepository.existsByUserIdAndStatus(userId, CashbackTransaction.TransactionStatus.PENDING);
        if (hasPending) {
            log.warn("Redemption blocked for user {}: A previous payout request is still processing.", userId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Payout Blocked: You have a pending redemption in progress. Please wait until it completes."
            ));
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User account profile records not found"));

            UserWallet wallet = walletRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Wallet information not found"));

            BigDecimal amountToRedeem = request.get("amount") != null
                    ? new BigDecimal(request.get("amount").toString())
                    : wallet.getCurrentBalance();

            // 🛑 RULE 2: ENFORCE MINIMUM LIMITS
            if (amountToRedeem.compareTo(minThreshold) < 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "error", "Minimum redemption amount is ₹30. Current request: ₹" + amountToRedeem
                ));
            }

            // 🛑 RULE 3: ABSOLUTE NEGATIVE BALANCE GUARD
            if (wallet.getCurrentBalance().compareTo(amountToRedeem) < 0 || wallet.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Rejecting deficit payout attempt! Balance: {}, Requested: {}", wallet.getCurrentBalance(), amountToRedeem);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "error", "Insufficient Funds: Your available rewards balance is too low for this redemption."
                ));
            }

            if (explicitTargetUpi != null && !explicitTargetUpi.isBlank()) {
                user.setUpiId(explicitTargetUpi);
                user.setSelectedUpi(explicitTargetUpi);
                userRepository.save(user);
            }

            // Safe to hand off to processing now that all security gates have passed successfully!
            walletService.redeemCashback(userId, amountToRedeem);
            return ResponseEntity.ok(Map.of("message", "Success! ₹" + amountToRedeem + " payout initiated successfully."));

        } catch (Exception e) {
            log.error("Redeem endpoint lock failure processing collapse:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/version")
    public String version() {
        return "v3-multi-upi-validation-ready";
    }
}