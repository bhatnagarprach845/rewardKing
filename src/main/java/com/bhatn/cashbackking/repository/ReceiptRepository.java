package com.bhatn.cashbackking.repository;



import com.bhatn.cashbackking.entity.Receipt;
import com.bhatn.cashbackking.entity.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    // 1. User History: For the "My Receipts" screen in your app
    List<Receipt> findByUserIdOrderByPurchaseDateDesc(String userId);

        // This allows you to fetch all receipts for a specific user
    List<Receipt> findByUserId(String userId);

    // 2. Fraud Prevention: Check if the exact same receipt was uploaded already
    // This is a "Three-Point Check" (Merchant + Date + Amount)
    boolean existsByMerchantNameAndTotalAmountAndPurchaseDate(
            String merchantName,
            BigDecimal totalAmount,
            LocalDate purchaseDate
    );

    @Query("SELECT COUNT(r) > 0 FROM Receipt r JOIN r.items i " +
            "WHERE r.id <> :currentId " + // <--- ADD THIS LINE
            "AND r.merchantName = :merchantName " +
            "AND r.totalAmount = :totalAmount " +
            "AND r.purchaseDate = :purchaseDate " +
            "AND i.description = :description " +
            "AND i.unitPrice = :unitPrice")
    boolean existsByDeepCheck(
            @Param("currentId") Long currentId,
            @Param("merchantName") String merchantName,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("purchaseDate") LocalDate purchaseDate,
            @Param("description") String description,
            @Param("unitPrice") BigDecimal unitPrice
    );

    boolean existsByMerchantNameAndTotalAmount(
            String merchantName,
            BigDecimal totalAmount
    );

    boolean existsByMerchantNameAndTotalAmountAndPurchaseDateNot(
            String merchantName,
            BigDecimal totalAmount,
            LocalDate purchaseDate
    );

    // 3. Analytics: Find the most popular merchants for cashback
    @Query("SELECT r.merchantName, COUNT(r) FROM Receipt r GROUP BY r.merchantName ORDER BY COUNT(r) DESC")
    List<Object[]> getMerchantPopularityReport();

    // 4. Processing Queue: Find all pending receipts for the background OCR worker
    List<Receipt> findByStatus(ReceiptStatus status);

    // 5. Regional Analytics: Find total spending by ZIP code
    @Query("SELECT r.merchantZipCode, SUM(r.totalAmount) FROM Receipt r GROUP BY r.merchantZipCode")
    List<Object[]> getSpendingByZipCode();

    boolean existsByFingerprintHashAndIdNot(String hash, Long id);
    // Supports your custom isCrossUserDuplicate Optional lookup
    List<Receipt> findByFingerprintHash(String fingerprintHash);

    // Dynamic query helper for deep data verification

}
