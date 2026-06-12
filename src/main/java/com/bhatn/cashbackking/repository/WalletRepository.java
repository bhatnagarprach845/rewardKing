package com.bhatn.cashbackking.repository;

import com.bhatn.cashbackking.entity.UserWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;



@Repository
public interface WalletRepository extends JpaRepository<UserWallet, String> {

    // Use this for the Dashboard (No Transaction/Lock required)
    Optional<UserWallet> findByUserId(String userId);

    // Use this in WalletService for adding cashback (Locking required)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserWallet w WHERE w.userId = :userId")
    Optional<UserWallet> findByUserIdForUpdate(String userId);

    @Query("SELECT w FROM UserWallet w WHERE w.currentBalance >= 30.00")
    List<UserWallet> findEligibleForPayout();

}