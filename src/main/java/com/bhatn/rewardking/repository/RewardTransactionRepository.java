package com.bhatn.rewardking.repository;

import com.bhatn.rewardking.entity.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {
    List<RewardTransaction> findTop10ByUserIdOrderByIdDesc(String userId);

    // Add this query method line
    List<RewardTransaction> findByType(String type);
}