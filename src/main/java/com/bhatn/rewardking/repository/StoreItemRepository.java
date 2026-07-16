package com.bhatn.rewardking.repository;

import com.bhatn.rewardking.entity.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface StoreItemRepository extends JpaRepository<StoreItem, String> {
    // Standard JpaRepository gives you findById(), findAll(), and save() out-of-the-box!

    /**
     * PESSIMISTIC LOCK: Locks the row (SELECT ... FOR UPDATE) during checkouts,
     * so concurrent redemptions can't oversell the same stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StoreItem s WHERE s.itemId = :itemId")
    Optional<StoreItem> findByIdForUpdate(String itemId);
}