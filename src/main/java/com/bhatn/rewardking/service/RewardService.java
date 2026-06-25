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

        // Pull history ledger rows
        List<RewardTransaction> transactions = transactionRepository.findTop10ByUserIdOrderByIdDesc(userId);

        List<TransactionDTO> dtos = transactions.stream().map(tx ->
                TransactionDTO.builder()
                        .id(String.valueOf(tx.getId()))
                        .amount(tx.getPointsAmount())
                        .type(tx.getType())
                        .date(tx.getProcessedAt() != null ? tx.getProcessedAt().toLocalDate().toString() : "")
                        // 🚀 USER VISIBILITY: Explicitly share the approval status and item info with the user
                        .status(tx.getStatus() != null ? tx.getStatus().name() : "PENDING")
                        //.notes(tx.getNotes())
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

        // Deduct Points upfront (escrow hold until admin approves or denies)
        wallet.setAvailablePoints(wallet.getAvailablePoints() - pointsCost);
        walletRepository.save(wallet);

        // Record entry inside history ledger
        RewardTransaction debitTransaction = new RewardTransaction();
        debitTransaction.setId(null);
        debitTransaction.setUserId(userId);
        debitTransaction.setPointsAmount(pointsCost);
        debitTransaction.setType("REDEEMED");

        // 🚀 ADMIN GATEWAY FIX: Set to PENDING so it populates the Admin Review board
        debitTransaction.setStatus(TransactionStatus.PENDING);
        debitTransaction.setProcessedAt(LocalDateTime.now());
        debitTransaction.setNotes("Order Placement: " + itemId);

        transactionRepository.save(debitTransaction);
    }
}