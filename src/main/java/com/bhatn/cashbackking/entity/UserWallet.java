package com.bhatn.cashbackking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_wallets")
public class UserWallet {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId; // AWS Cognito Sub/Username

    @Builder.Default
    @Column(name = "current_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Version
    private Long version; // Optimistic locking to prevent race conditions during concurrent updates

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Logic for the ₹30 threshold.
     * Useful for the Payout Service to trigger notifications.
     */
    public boolean isEligibleForPayout() {
        return currentBalance != null && currentBalance.compareTo(new BigDecimal("30.00")) >= 0;
    }

    /**
     * Helper method to safely add cashback.
     */
    public void addBalance(BigDecimal amount) {
        if (amount != null) {
            this.currentBalance = this.currentBalance.add(amount);
        }
    }
}