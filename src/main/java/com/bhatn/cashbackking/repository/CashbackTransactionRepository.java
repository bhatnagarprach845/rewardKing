package com.bhatn.cashbackking.repository;



import com.bhatn.cashbackking.entity.CashbackTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, Long> {

    // 1. Basic lookup for a specific user's history
    List<CashbackTransaction> findByUserIdOrderByProcessedAtDesc(String userId);

    List<CashbackTransaction> findByStatusIn(List<CashbackTransaction.TransactionStatus> statuses);

    // 2. Audit check: Find the transaction associated with a specific receipt
    CashbackTransaction findByReceiptId(Long receiptId);

    // 3. Analytics: Calculate total cashback awarded in a date range
    @Query("SELECT SUM(t.amountAwarded) FROM CashbackTransaction t WHERE t.processedAt BETWEEN :start AND :end")
    BigDecimal getTotalAwardedInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 4. Analytics: Find the "Top Earners" who are frequently hitting the ₹30 threshold
    @Query("SELECT t.userId, SUM(t.amountAwarded) as total FROM CashbackTransaction t " +
            "GROUP BY t.userId ORDER BY total DESC")
    List<Object[]> getTopEarners();

    // In CashbackTransactionRepository.java
    List<CashbackTransaction> findByStatus(CashbackTransaction.TransactionStatus status);

    /**
     * Calculate the "Live" balance for a user based on transactions
     * that haven't been redeemed yet.
     */
    @Query("SELECT SUM(t.amountAwarded) FROM CashbackTransaction t " +
            "WHERE t.userId = :userId AND t.status = 'COMPLETED'")
    BigDecimal getTotalAvailableForUser(@Param("userId") String userId);

    // Add this line to fix the error in WalletService
    List<CashbackTransaction> findByUserIdAndStatus(String userId, CashbackTransaction.TransactionStatus status);

    Optional<CashbackTransaction> findByPayoutId(String payoutId);

    Optional<CashbackTransaction> findByRazorpayPayoutId(String razorpayPayoutId);



}