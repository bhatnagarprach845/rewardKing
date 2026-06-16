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

    /**
     * Fetch user's current points balance and historical points ledger.
     */
    @Transactional(readOnly = true)
    public PayoutStatusResponse getUserPointsDashboard(String userId) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User wallet not found for: " + userId));

        List<RewardTransaction> transactions = transactionRepository.findTop10ByUserIdOrderByIdDesc(userId);

        List<TransactionDTO> dtos = transactions.stream().map(tx ->
                TransactionDTO.builder()
                        .id(String.valueOf(tx.getId())) // Converts Long ID securely to String for DTO
                        .amount(tx.getPointsAmount())
                        .type(tx.getType())
                        .date(tx.getProcessedAt() != null ? tx.getProcessedAt().toLocalDate().toString() : "")
                        .status(tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED")
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

    /**
     * Circuit breaker validation logic to handle points checkouts safely.
     */
    @Transactional
    public void processPointsRedemption(String userId, String itemId, long pointsCost) {
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user profile instance."));

        // Strict Circuit-Breaker: Validate points sufficiency
        if (wallet.getAvailablePoints() < pointsCost) {
            throw new IllegalArgumentException("Insufficient points balance. Transaction blocked.");
        }

        // Atomically Deduct Points from Core Balance Row
        wallet.setAvailablePoints(wallet.getAvailablePoints() - pointsCost);
        walletRepository.save(wallet);

        // Record a DEBIT entry directly inside your transaction history ledger
        RewardTransaction debitTransaction = new RewardTransaction();
        debitTransaction.setId(null); // Set to null so database handles auto-increment identity strategy
        debitTransaction.setUserId(userId);
        debitTransaction.setPointsAmount(pointsCost);
        debitTransaction.setType("REDEEMED");
        debitTransaction.setStatus(TransactionStatus.COMPLETED); // Uses type-safe Enum assignment
        debitTransaction.setProcessedAt(LocalDateTime.now());   // Aligned to entity LocalDateTime definition
        debitTransaction.setNotes("Redeemed item catalog identifier: " + itemId);

        transactionRepository.save(debitTransaction);
    }
}