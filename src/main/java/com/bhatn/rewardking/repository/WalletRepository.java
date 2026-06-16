package com.bhatn.rewardking.repository;

import com.bhatn.rewardking.entity.UserWallet; // Double-check if your package is .entity or .model
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<UserWallet, String> {

    // 1. Used for the Dashboard (No Transaction/Lock overhead required)
    Optional<UserWallet> findByUserId(String userId);

    // 2. Used for points modifications to prevent double-spending concurrency bugs
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserWallet w WHERE w.userId = :userId")
    Optional<UserWallet> findByUserIdForUpdate(String userId);

    // 3. Updated to support your new points threshold approach (e.g., users with 100+ points)
    @Query("SELECT w FROM UserWallet w WHERE w.availablePoints >= 100")
    List<UserWallet> findEligibleForMilestoneRewards();
}