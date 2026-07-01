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
import com.bhatn.rewardking.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final ReceiptRepository receiptRepository;
    private final RewardTransactionRepository transactionRepository;
    private final RewardService rewardService;

    @GetMapping("/transactions")
    public List<RewardTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @GetMapping("/payouts")
    public List<PayoutDTO> getRedeemedHistory() {
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

    @PostMapping("/payouts/update-status/{transactionId}")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable Long transactionId,
            @RequestParam String newStatus,
            @RequestParam(required = false) String trackingNumber) {
        try {
            rewardService.updateOrderStatusWithTracking(transactionId, newStatus, trackingNumber);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/wallets")
    public ResponseEntity<Page<AdminWalletDTO>> getAdminWallets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserWallet> walletPage = walletRepository.findAll(pageable);

        List<String> userIds = walletPage.getContent().stream()
                .map(UserWallet::getUserId)
                .distinct()
                .toList();

        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getCognitoId, Function.identity()));

        Page<AdminWalletDTO> resultPage = walletPage.map(wallet -> {
            User user = userMap.get(wallet.getUserId());

            return AdminWalletDTO.builder()
                    .userId(wallet.getUserId())
                    .fullName(wallet.getFullName() != null ? wallet.getFullName() : (user != null ? user.getName() : "Unknown User"))
                    .email(wallet.getEmail() != null ? wallet.getEmail() : (user != null ? user.getEmail() : "N/A"))
                    .currentBalance(wallet.getAvailablePoints())
                    .lastUpdated(wallet.getLastUpdated())
                    .build();
        });

        return ResponseEntity.ok(resultPage);
    }

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