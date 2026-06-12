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

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
//@CrossOrigin(origins = "https://feature-initialcommit.dwp81oqt95zeu.amplifyapp.com")
public class AdminController {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;

    private final CashbackTransactionRepository transactionRepository;

    private final PayoutService payoutService; // Inject the service

    @GetMapping("/transactions")
    public List<CashbackTransaction> getAllTransactions() {
        // This will return everything: COMPLETED (earnings) and REDEEMED (payouts)
        return transactionRepository.findAll();
    }

    @PostMapping("/payouts/initiate/{userId}")
    public ResponseEntity<String> initiatePayout(@PathVariable String userId, @RequestBody Map<String, BigDecimal> payload) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal amount = payload.get("amount");

        String rzpId = payoutService.triggerPayout(user, amount);

        // Create a record so the Webhook knows what this is
        CashbackTransaction tx = new CashbackTransaction();
        tx.setUserId(userId);
        tx.setAmountAwarded(amount);
        tx.setStatus(CashbackTransaction.TransactionStatus.APPROVED);
        tx.setRazorpayPayoutId(rzpId);
        tx.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        // 2. CRITICAL: Update the User's Wallet in the DB
        Optional<UserWallet> wallet = walletRepository.findByUserId(userId);
        if (wallet.isPresent()) {
            wallet.get().setCurrentBalance(BigDecimal.ZERO); // Reset to 0
            wallet.get().setLastUpdated(LocalDateTime.now());
            walletRepository.save(wallet.get());
        }

        return ResponseEntity.ok("Payout initiated successfully" + rzpId);
    }
    @PostMapping("/payouts/approve/{requestId}")
    public ResponseEntity<String> approvePayout(@PathVariable Long requestId) {
        CashbackTransaction req = transactionRepository.findById(requestId).orElseThrow();
        User user = userRepository.findById(req.getUserId()).orElseThrow();

        BigDecimal positiveAmount = req.getAmountAwarded().abs();
        // 1. Call Razorpay
        String rzpPayoutId = payoutService.triggerPayout(user, positiveAmount);

        // 2. CRITICAL: Deduct the amount from the actual Wallet table in the DB
        UserWallet wallet = walletRepository.findByUserId(req.getUserId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // Subtract the absolute amount from the current balance
        BigDecimal newBalance = wallet.getCurrentBalance().subtract(req.getAmountAwarded().abs());
        wallet.setCurrentBalance(newBalance);
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        // 3. Update status to APPROVED (Wait for Webhook for SUCCESS)
        req.setStatus(CashbackTransaction.TransactionStatus.APPROVED);
        req.setRazorpayPayoutId(rzpPayoutId);
        req.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(req);

        return ResponseEntity.ok("Payout initiated with Razorpay ID: " + rzpPayoutId + " and New Balance: " + newBalance);
    }
    @GetMapping("/payouts")
    public List<PayoutDTO> getRedeemedHistory() {

        // 1. Fetch BOTH statuses so no request is missed
        List<CashbackTransaction.TransactionStatus> actionableStatuses =
                List.of(CashbackTransaction.TransactionStatus.PENDING, CashbackTransaction.TransactionStatus.REDEEMED);
        // This fetches only the "REDEEMED" rows for the Admin
        List<CashbackTransaction> redemptions = transactionRepository.findByStatusIn(actionableStatuses);
        return redemptions.stream().map(tx -> {
            Optional<String> fullname = userRepository.findById(tx.getUserId()).map(User::getName);

            return PayoutDTO.builder()
                    .id(tx.getId())
                    .userId(tx.getUserId())
                    .userName(fullname.orElse( "Unknown User"))
                    .amountAwarded(tx.getAmountAwarded())
                    .processedAt(tx.getProcessedAt())
                    .status(tx.getStatus().toString())
                    .build();
        }).toList();
    }
    @GetMapping("/payouts/report")
    public ResponseEntity<String> exportPayoutsCsv() {
        // Specifically fetch only the redemptions for a payout report
        List<CashbackTransaction> redemptions = transactionRepository.findByStatus(
                CashbackTransaction.TransactionStatus.REDEEMED);

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Amount Redeemed,Processed Date\n");

        for (CashbackTransaction tx : redemptions) {

            Optional<String> fullname = userRepository.findById(tx.getUserId()).map(User::getName);
            csv.append(fullname.orElse("Unknown User")).append(",")
                    .append(tx.getAmountAwarded()).append(",")
                    .append(tx.getProcessedAt()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=payout_history.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }
 /*   @GetMapping("/wallets")
    // For now, we'll keep it simple, but you can add Role-based security here later
    public List<UserWallet> getAllWallets() {
        return walletRepository.findAll();
    }*/

    @GetMapping("/wallets/export")
    public ResponseEntity<String> exportWalletsCsv() {
        List<UserWallet> wallets = walletRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Current Balance,Last Updated\n"); // Header

        for (UserWallet wallet : wallets) {
            Optional<String> fullname = userRepository.findById(wallet.getUserId()).map(User::getName);
            csv.append(fullname.orElse(null)).append(",")
                    .append(wallet.getCurrentBalance()).append(",")
                    .append(wallet.getLastUpdated()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=wallets_report.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    @GetMapping("/wallets")
    public ResponseEntity<List<AdminWalletDTO>> getAdminWallets() {
        return ResponseEntity.ok(walletRepository.findAll().stream().map(wallet -> {
            // Fetch the user details to get the name
            User user = userRepository.findById(wallet.getUserId()).orElse(null);

            return AdminWalletDTO.builder()
                    .userId(wallet.getUserId())
                    .fullName(user != null ? user.getName() : "Unknown User")
                    .email(user != null ? user.getEmail() : "N/A")
                    .upiId(user != null ? user.getUpiId() : "N/A")
                    .currentBalance(wallet.getCurrentBalance())
                    .lastUpdated(wallet.getLastUpdated())
                    .build();
        }).toList());
    }

    // 2. Get all receipts for a specific user
    @GetMapping("/users/{userId}/receipts")
    public ResponseEntity<List<Receipt>> getUserReceipts(@PathVariable String userId) {
        List<Receipt> receipts = receiptRepository.findByUserId(userId);
        return ResponseEntity.ok(receipts);
    }

    /**
     * Fetch specific profile details for the "User Profile" tab
     */
    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<User> getUserProfile(@PathVariable String userId) {
        return userRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<List<CashbackTransaction>> getUserTransactionHistory(@PathVariable String userId) {
        // This should return both COMPLETED (earnings) and SETTLED/APPROVED (payouts)
        List<CashbackTransaction> history = transactionRepository.findByUserIdOrderByProcessedAtDesc(userId);
        return ResponseEntity.ok(history);
    }
}