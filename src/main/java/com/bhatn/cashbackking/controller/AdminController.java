package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.dto.AdminWalletDTO;
import com.bhatn.cashbackking.dto.PayoutDTO;
import com.bhatn.cashbackking.entity.*;
import com.bhatn.cashbackking.repository.CashbackTransactionRepository;
import com.bhatn.cashbackking.repository.ReceiptRepository;
import com.bhatn.cashbackking.repository.UserRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import com.bhatn.cashbackking.service.payment_del.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
//@CrossOrigin(origins = "https://feature-initialcommit.dwp81oqt95zeu.amplifyapp.com")
public class AdminController {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;
    private final CashbackTransactionRepository transactionRepository;
    private final PayoutService payoutService;

    @GetMapping("/transactions")
    public List<CashbackTransaction> getAllTransactions() {
        // This will return everything: COMPLETED (earnings) and REDEEMED (payouts)
        return transactionRepository.findAll();
    }

    /**
     * Initiates a manual payout for a user.
     *
     * FIX (Critical): The wallet balance is NO LONGER zeroed here.
     * Previously the balance was reset to zero immediately after calling Razorpay,
     * meaning a Razorpay failure would silently wipe the user's balance with no refund path.
     *
     * The correct flow is:
     *   1. Record the transaction as APPROVED (intent).
     *   2. Trigger Razorpay — get a payout ID.
     *   3. Store the Razorpay payout ID on the transaction.
     *   4. The wallet deduction happens ONLY inside RazorpayWebhookController
     *      when "payout.processed" is confirmed, OR the balance is refunded on
     *      "payout.reversed" / "payout.failed".
     */
    @PostMapping("/payouts/initiate/{userId}")
    public ResponseEntity<String> initiatePayout(
            @PathVariable String userId,
            @RequestBody Map<String, BigDecimal> payload) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal amount = payload.get("amount");

        // Trigger the Razorpay payout — get back the Razorpay payout reference ID
        String rzpId = payoutService.triggerPayout(user, amount);

        // Record the transaction as APPROVED and store the Razorpay reference.
        // Wallet deduction is deferred to the webhook (RazorpayWebhookController).
        CashbackTransaction tx = new CashbackTransaction();
        tx.setUserId(userId);
        tx.setAmountAwarded(amount.negate()); // Store as negative to indicate a withdrawal
        tx.setStatus(CashbackTransaction.TransactionStatus.APPROVED);
        tx.setRazorpayPayoutId(rzpId);
        tx.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        return ResponseEntity.ok("Payout initiated. Razorpay ID: " + rzpId
                + ". Wallet will be updated once Razorpay confirms via webhook.");
    }

    /**
     * Approves an existing PENDING redemption request and sends it to Razorpay.
     *
     * FIX (Critical): Wallet deduction moved to webhook handler.
     * Previously the balance was subtracted here before Razorpay confirmed success.
     */
    @PostMapping("/payouts/approve/{requestId}")
    public ResponseEntity<String> approvePayout(@PathVariable Long requestId) {
        CashbackTransaction req = transactionRepository.findById(requestId).orElseThrow();
        User user = userRepository.findById(req.getUserId()).orElseThrow();

        BigDecimal positiveAmount = req.getAmountAwarded().abs();
        // 1. Call Razorpay
        String rzpPayoutId = payoutService.triggerPayout(user, positiveAmount);

        // 2. Mark the transaction as APPROVED and record the Razorpay reference.
        //    Wallet deduction now happens in RazorpayWebhookController on "payout.processed".
        req.setStatus(CashbackTransaction.TransactionStatus.APPROVED);
        req.setRazorpayPayoutId(rzpPayoutId);
        req.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(req);

        return ResponseEntity.ok("Payout submitted to Razorpay. ID: " + rzpPayoutId
                + ". Wallet will be debited once Razorpay confirms via webhook.");
    }

    /**
     * Returns all PENDING and REDEEMED payout requests for the admin dashboard.
     *
     * FIX (High): Eliminated N+1 query. Previously userRepository.findById() was
     * called inside the stream for every transaction row. Now all relevant users
     * are fetched in a single query and looked up from a Map.
     */
    @GetMapping("/payouts")
    public List<PayoutDTO> getRedeemedHistory() {
        List<CashbackTransaction.TransactionStatus> actionableStatuses =
                List.of(CashbackTransaction.TransactionStatus.PENDING,
                        CashbackTransaction.TransactionStatus.REDEEMED);

        List<CashbackTransaction> redemptions = transactionRepository.findByStatusIn(actionableStatuses);

        // Batch-fetch all users needed for this result set in one query
        List<String> userIds = redemptions.stream().map(CashbackTransaction::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        return redemptions.stream().map(tx -> {
            User user = userMap.get(tx.getUserId());
            return PayoutDTO.builder()
                    .id(tx.getId())
                    .userId(tx.getUserId())
                    .userName(user != null ? user.getName() : "Unknown User")
                    .amountAwarded(tx.getAmountAwarded())
                    .processedAt(tx.getProcessedAt())
                    .status(tx.getStatus().toString())
                    .build();
        }).toList();
    }

    /**
     * Exports a CSV of all redeemed payouts.
     *
     * FIX (High): Batch-fetches users instead of querying per-row.
     */
    @GetMapping("/payouts/report")
    public ResponseEntity<String> exportPayoutsCsv() {
        // Specifically fetch only the redemptions for a payout report
        List<CashbackTransaction> redemptions = transactionRepository.findByStatus(
                CashbackTransaction.TransactionStatus.REDEEMED);

        List<String> userIds = redemptions.stream().map(CashbackTransaction::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Amount Redeemed,Processed Date\n");

        for (CashbackTransaction tx : redemptions) {
            User user = userMap.get(tx.getUserId());
            csv.append(user != null ? user.getName() : "Unknown User").append(",")
                    .append(tx.getAmountAwarded()).append(",")
                    .append(tx.getProcessedAt()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=payout_history.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    /**
     * Exports a CSV of all user wallets.
     *
     * FIX (High): Batch-fetches users instead of querying per-row.
     */
    @GetMapping("/wallets/export")
    public ResponseEntity<String> exportWalletsCsv() {
        List<UserWallet> wallets = walletRepository.findAll();

        List<String> userIds = wallets.stream().map(UserWallet::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Full Name,Current Balance,Last Updated\n");

        for (UserWallet wallet : wallets) {
            User user = userMap.get(wallet.getUserId());
            csv.append(user != null ? user.getName() : "Unknown User").append(",")
                    .append(user != null ? user.getEmail() : "N/A").append(",")
                    .append(wallet.getCurrentBalance()).append(",")
                    .append(wallet.getLastUpdated()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=wallets_report.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    /**
     * Returns all wallets enriched with user details for the admin dashboard.
     *
     * FIX (High): Batch-fetches users instead of querying per-row.
     */
    @GetMapping("/wallets")
    public ResponseEntity<List<AdminWalletDTO>> getAdminWallets() {
        List<UserWallet> wallets = walletRepository.findAll();

        List<String> userIds = wallets.stream().map(UserWallet::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        List<AdminWalletDTO> result = wallets.stream().map(wallet -> {
            User user = userMap.get(wallet.getUserId());
            return AdminWalletDTO.builder()
                    .userId(wallet.getUserId())
                    .fullName(user != null ? user.getName() : "Unknown User")
                    .email(user != null ? user.getEmail() : "N/A")
                    .upiId(user != null ? user.getUpiId() : "N/A")
                    .currentBalance(wallet.getCurrentBalance())
                    .lastUpdated(wallet.getLastUpdated())
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{userId}/receipts")
    public ResponseEntity<List<Receipt>> getUserReceipts(@PathVariable String userId) {
        return ResponseEntity.ok(receiptRepository.findByUserId(userId));
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<User> getUserProfile(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<List<CashbackTransaction>> getUserTransactionHistory(@PathVariable String userId) {
        return ResponseEntity.ok(transactionRepository.findByUserIdOrderByProcessedAtDesc(userId));
    }
}