package com.bhatn.rewardking.service;

import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.dto.PayoutStatusResponse.TransactionDTO;
import com.bhatn.rewardking.entity.RewardTransaction;
import com.bhatn.rewardking.entity.RewardTransaction.TransactionStatus;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RewardService {

    private final WalletRepository walletRepository;
    private final RewardTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PayoutStatusResponse getUserPointsDashboard(String userId) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User wallet not found for: " + userId));

        // Pull history ledger rows matching the Cognito UUID string
        List<RewardTransaction> transactions = transactionRepository.findTop10ByUserIdOrderByIdDesc(userId);

        List<TransactionDTO> dtos = transactions.stream().map(tx ->
                TransactionDTO.builder()
                        .id(String.valueOf(tx.getId()))
                        .amount(tx.getPointsAmount())
                        .type(tx.getType())
                        .date(tx.getProcessedAt() != null ? tx.getProcessedAt().toLocalDate().toString() : "")
                        // 🚀 REACT SYNC FIX: Explicitly append alternative naming fields to guarantee mapping matches
                        .processedAt(tx.getProcessedAt() != null ? tx.getProcessedAt().toString() : "")
                        .status(tx.getStatus() != null ? tx.getStatus().name() : "PENDING")
                        // 🚀 RE-ENABLED NOTES: Crucial for displaying merchandise name strings across dashboards
                        .notes(tx.getNotes())
                        .build()
        ).collect(Collectors.toList());

        return PayoutStatusResponse.builder()
                .name(wallet.getFullName())
                .email(wallet.getEmail())
                .currentBalance(wallet.getAvailablePoints())
                .threshold(1000L)
                .statusMessage("Keep scanning bills to claim bigger items!")
                .recentTransactions(dtos)
                .build();
    }

    @Transactional
    public void processPointsRedemption(String userId, String itemId, long pointsCost) {
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user profile instance."));

        if (wallet.getAvailablePoints() < pointsCost) {
            throw new IllegalArgumentException("Insufficient points balance. Transaction blocked.");
        }

        // Deduct points from primary wallet row on initial checkout request placement (Escrow)
        wallet.setAvailablePoints(wallet.getAvailablePoints() - pointsCost);
        walletRepository.save(wallet);

        // Record entry inside history database ledger table
        RewardTransaction debitTransaction = new RewardTransaction();
        debitTransaction.setId(null);
        debitTransaction.setUserId(userId);
        debitTransaction.setPointsAmount(pointsCost);
        debitTransaction.setType("REDEEMED");

        // 🚀 SET PENDING STATE: Placed into admin review pipeline rather than auto-completing
        debitTransaction.setStatus(TransactionStatus.PENDING);
        debitTransaction.setProcessedAt(LocalDateTime.now());
        debitTransaction.setNotes("Order Placement: " + itemId);

        transactionRepository.save(debitTransaction);
    }
}