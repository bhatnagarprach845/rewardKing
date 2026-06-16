package com.bhatn.rewardking.repository;

import com.bhatn.rewardking.entity.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, String> {

    // 1. Fetch complete chronological ledger streams for a distinct user
    List<RewardTransaction> findByUsernameOrderByIdDesc(String username);

    List<RewardTransaction> findTop10ByUsernameOrderByIdDesc(String username);

    List<RewardTransaction> findTop10ByUserIdOrderByIdDesc(String userId);

    // 2. Audit verification lookup: Match transaction properties back to processed receipts
    List<RewardTransaction> findByReceiptId(String receiptId);

    // 3. Analytics: Sum up total points values allocated inside the ecosystem
    @Query("SELECT COALESCE(SUM(t.pointsAmount), 0) FROM RewardTransaction t WHERE t.type = 'EARNED'")
    long getTotalPointsAwarded();

    // 4. Leaderboard Metrics: Extract top profiles saving up loyalty items
    @Query("SELECT t.username, SUM(t.pointsAmount) as total FROM RewardTransaction t " +
            "WHERE t.type = 'EARNED' GROUP BY t.username ORDER BY total DESC")
    List<Object[]> getTopEarners();
}