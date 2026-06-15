package com.bhatn.rewardking.repository;



import com.bhatn.rewardking.entity.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.bhatn.rewardking.entity.RewardTransaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    // 1. Basic lookup for a specific user's history
    List<RewardTransaction> findByUserIdOrderByProcessedAtDesc(String userId);

    List<RewardTransaction> findByStatusIn(List<RewardTransaction.TransactionStatus> statuses);

    // 2. Audit check: Find the transaction associated with a specific receipt
    RewardTransaction findByReceiptId(Long receiptId);

    // 3. Analytics: Calculate total Reward awarded in a date range
    @Query("SELECT SUM(t.amountAwarded) FROM RewardTransaction t WHERE t.processedAt BETWEEN :start AND :end")
    BigDecimal getTotalAwardedInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 4. Analytics: Find the "Top Earners" who are frequently hitting the ₹30 threshold
    @Query("SELECT t.userId, SUM(t.amountAwarded) as total FROM RewardTransaction t " +
            "GROUP BY t.userId ORDER BY total DESC")
    List<Object[]> getTopEarners();

    // In RewardTransactionRepository.java
    List<RewardTransaction> findByStatus(RewardTransaction.TransactionStatus status);

    /**
     * Calculate the "Live" balance for a user based on transactions
     * that haven't been redeemed yet.
     */
    @Query("SELECT SUM(t.amountAwarded) FROM RewardTransaction t " +
            "WHERE t.userId = :userId AND t.status = 'COMPLETED'")
    BigDecimal getTotalAvailableForUser(@Param("userId") String userId);

    // Add this line to fix the error in WalletService
    List<RewardTransaction> findByUserIdAndStatus(String userId, RewardTransaction.TransactionStatus status);

    Optional<RewardTransaction> findByPayoutId(String payoutId);

    Optional<RewardTransaction> findByRazorpayPayoutId(String razorpayPayoutId);

    // 🔒 THE GATEKEEPER: Checks if a user already has an unapproved payout running
    boolean existsByUserIdAndStatus(String userId, TransactionStatus status);



}