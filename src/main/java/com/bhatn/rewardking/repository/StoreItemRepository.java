package com.bhatn.rewardking.repository;

import com.bhatn.rewardking.entity.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreItemRepository extends JpaRepository<StoreItem, String> {
    // Standard JpaRepository gives you findById(), findAll(), and save() out-of-the-box!
}