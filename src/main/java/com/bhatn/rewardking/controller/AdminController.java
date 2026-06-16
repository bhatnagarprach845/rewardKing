package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.AdminWalletDTO;
import com.bhatn.rewardking.dto.PayoutDTO;
import com.bhatn.rewardking.entity.Receipt;
import com.bhatn.rewardking.entity.RewardTransaction;
import com.bhatn.rewardking.entity.User;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.ReceiptRepository;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.UserRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;
    private final RewardTransactionRepository transactionRepository;

    /**
     * Fetch all historical transactions logged in the ecosystem.
     */
    @GetMapping("/transactions")
    public List<RewardTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    /**
     * Returns points redemption history logs for administrative dashboard views.
     */
    @GetMapping("/payouts")
    public List<PayoutDTO> getRedeemedHistory() {
        // OPTIMIZATION: Switched to repository filtering to keep memory footprints safe
        List<RewardTransaction> redemptions = transactionRepository.findByType("REDEEMED");

        List<String> userIds = redemptions.stream().map(RewardTransaction::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        return redemptions.stream().map(tx -> {
            User user = userMap.get(tx.getUserId());
            return PayoutDTO.builder()
                    .id(tx.getId())
                    .userId(tx.getUserId())
                    .userName(user != null ? user.getName() : "Unknown User")
                    .amountAwarded(tx.getPointsAmount())
                    .processedAt(tx.getProcessedAt())
                    .status(tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED")
                    .build();
        }).toList();
    }

    /**
     * Exports a clean CSV compilation of catalog item redemptions.
     */
    @GetMapping("/payouts/report")
    public ResponseEntity<String> exportPayoutsCsv() {
        List<RewardTransaction> redemptions = transactionRepository.findByType("REDEEMED");

        List<String> userIds = redemptions.stream().map(RewardTransaction::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        StringBuilder csv = new StringBuilder();
        csv.append("User Name,Points Redeemed,Item Details,Processed Date\n");

        for (RewardTransaction tx : redemptions) {
            User user = userMap.get(tx.getUserId());
            csv.append(user != null ? user.getName() : "Unknown User").append(",")
                    .append(tx.getPointsAmount()).append(",")
                    .append(tx.getNotes() != null ? tx.getNotes().replace(",", ";") : "N/A").append(",")
                    .append(tx.getProcessedAt()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=points_redemption_history.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    /**
     * Exports a CSV of user wallets tracking point values.
     */
    @GetMapping("/wallets/export")
    public ResponseEntity<String> exportWalletsCsv() {
        List<UserWallet> wallets = walletRepository.findAll();

        List<String> userIds = wallets.stream().map(UserWallet::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        StringBuilder csv = new StringBuilder();
        csv.append("User ID,Email,Available Points,Last Updated\n");

        for (UserWallet wallet : wallets) {
            User user = userMap.get(wallet.getUserId());
            csv.append(wallet.getUserId()).append(",")
                    .append(user != null ? user.getEmail() : "N/A").append(",")
                    .append(wallet.getAvailablePoints()).append(",")
                    .append(wallet.getLastUpdated()).append("\n");
        }

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=user_points_report.csv")
                .header("Content-Type", "text/csv")
                .body(csv.toString());
    }

    /**
     * Returns all user wallets enriched with metadata descriptors for admin panels.
     */
    @GetMapping("/wallets")
    public ResponseEntity<List<AdminWalletDTO>> getAdminWallets() {
        List<UserWallet> wallets = walletRepository.findAll();

        List<String> userIds = wallets.stream().map(UserWallet::getUserId).distinct().toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        List<AdminWalletDTO> result = wallets.stream().map(wallet -> {
            User user = userMap.get(wallet.getUserId());

            // FIX: If currentBalance requires a BigDecimal, we typecast it explicitly here.
            // If your DTO has already been updated to a long primitive, you can change this back to wallet.getAvailablePoints()
            BigDecimal balanceValue = BigDecimal.valueOf(wallet.getAvailablePoints());

            return AdminWalletDTO.builder()
                    .userId(wallet.getUserId())
                    .fullName(wallet.getFullName() != null ? wallet.getFullName() : (user != null ? user.getName() : "Unknown User"))
                    .email(wallet.getEmail() != null ? wallet.getEmail() : (user != null ? user.getEmail() : "N/A"))
                    .currentBalance(wallet.getAvailablePoints())
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
    public ResponseEntity<List<RewardTransaction>> getUserTransactionHistory(@PathVariable String userId) {
        return ResponseEntity.ok(transactionRepository.findTop10ByUserIdOrderByIdDesc(userId));
    }
}