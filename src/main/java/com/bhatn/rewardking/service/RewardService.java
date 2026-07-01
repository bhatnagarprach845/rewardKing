package com.bhatn.rewardking.service;

import com.bhatn.rewardking.controller.RewardController.RedeemPointsRequest;
import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.dto.PayoutStatusResponse.TransactionDTO;
import com.bhatn.rewardking.entity.RewardTransaction;
import com.bhatn.rewardking.entity.RewardTransaction.TransactionStatus;
import com.bhatn.rewardking.entity.StoreItem;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.StoreItemRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardService {

    private final WalletRepository walletRepository;
    private final RewardTransactionRepository transactionRepository;
    private final StoreItemRepository storeItemRepository;

    @Transactional(readOnly = true)
    public List<StoreItem> getAllStoreItems() {
        return storeItemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public PayoutStatusResponse getUserPointsDashboard(String userId) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User wallet not found for: " + userId));

        List<RewardTransaction> transactions = transactionRepository.findTop10ByUserIdOrderByIdDesc(userId);

        List<TransactionDTO> dtos = transactions.stream().map(tx ->
                TransactionDTO.builder()
                        .id(String.valueOf(tx.getId()))
                        .amount(tx.getPointsAmount())
                        .type(tx.getType())
                        .date(tx.getProcessedAt() != null ? tx.getProcessedAt().toLocalDate().toString() : "")
                        .processedAt(tx.getProcessedAt() != null ? tx.getProcessedAt().toString() : "")
                        .status(tx.getStatus() != null ? tx.getStatus().name() : "PENDING")
                        .notes(tx.getNotes())
                        .trackingnumber(tx.getTrackingNumber())
                        .build()
        ).collect(Collectors.toList());

        return PayoutStatusResponse.builder()
                .name(wallet.getFullName())
                .email(wallet.getEmail())
                .currentBalance(wallet.getAvailablePoints())
                .statusMessage("Keep scanning bills to claim bigger items!")
                .recentTransactions(dtos)
                .build();
    }

    /**
     * 🚀 TRANSACTION GUARANTEED WITH PESSIMISTIC LOCKING:
     * Prevents race conditions and duplicate checkouts by locking the user's wallet row.
     */
    @Transactional
    public void processBulkCartRedemption(String userId, List<RedeemPointsRequest.CartItemDTO> cartItems) {
        // 🚀 CRITICAL SECURE UPDATE: Using findByUserIdForUpdate instead of standard read to guard points balance
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user profile instance."));

        long totalCartCost = 0;
        for (RedeemPointsRequest.CartItemDTO item : cartItems) {
            StoreItem storeItem = storeItemRepository.findById(item.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + item.getItemId()));

            if (storeItem.getStockLevel() < item.getQuantity()) {
                throw new IllegalArgumentException("Item out of stock: " + storeItem.getName());
            }
            totalCartCost += storeItem.getPointsCost() * item.getQuantity();
        }

        if (wallet.getAvailablePoints() < totalCartCost) {
            throw new IllegalArgumentException("Insufficient points balance. Cart checkout blocked.");
        }

        // Apply deductions atomically
        wallet.setAvailablePoints(wallet.getAvailablePoints() - totalCartCost);
        walletRepository.save(wallet);

        for (RedeemPointsRequest.CartItemDTO item : cartItems) {
            StoreItem storeItem = storeItemRepository.findById(item.getItemId()).get();

            // Decrement Stock Level
            storeItem.setStockLevel(storeItem.getStockLevel() - item.getQuantity());
            storeItemRepository.save(storeItem);

            // Record fulfillment request row
            RewardTransaction debitTransaction = RewardTransaction.builder()
                    .userId(userId)
                    .pointsAmount(storeItem.getPointsCost() * item.getQuantity())
                    .type("REDEEMED")
                    .status(TransactionStatus.PENDING)
                    .processedAt(LocalDateTime.now())
                    .notes("Order Placement: " + item.getItemId())
                    .build();

            transactionRepository.save(debitTransaction);
        }
    }

    @Transactional(readOnly = true)
    public Page<UserWallet> getPaginatedWallets(Pageable pageable) {
        return walletRepository.findAll(pageable);
    }

    @Transactional
    public void updateOrderStatusWithTracking(Long transactionId, String newStatus, String trackingNumber) {
        RewardTransaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Order record not found."));

        tx.setStatus(RewardTransaction.TransactionStatus.valueOf(newStatus.toUpperCase()));
        if (trackingNumber != null && !trackingNumber.trim().isEmpty()) {
            tx.setTrackingNumber(trackingNumber.trim());
        }
        tx.setProcessedAt(LocalDateTime.now());
        transactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserProfileData(String userId) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile records missing from wallet storage line."));

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", wallet.getFullName());
        profile.put("email", wallet.getEmail());
        profile.put("address", wallet.getAddress() != null ? wallet.getAddress() : "");
        profile.put("phoneNumber", wallet.getPhonenumber() != null ? wallet.getPhonenumber() : "");
        return profile;
    }

    @Transactional
    public void updateUserProfileData(String userId, Map<String, String> payload) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Target wallet context index mismatch."));

        wallet.setAddress(payload.get("address"));
        wallet.setPhonenumber(payload.get("phoneNumber"));
        walletRepository.save(wallet);
    }
}