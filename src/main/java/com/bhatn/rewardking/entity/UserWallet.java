package com.bhatn.rewardking.entity;

import jakarta.persistence.*;
import lombok.*;
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

    // FIX 1: Migrated from BigDecimal to long to support points natively
    @Builder.Default
    @Column(name = "available_points", nullable = false)
    private long availablePoints = 0L;

    // Added fields used by your RewardService dashboard response mapping
    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Version
    private Long version; // Optimistic locking guard rails for parallel execution threads

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Helper method to safely credit newly earned points from scanned bills.
     */
    public void addPoints(long points) {
        if (points > 0) {
            this.availablePoints += points;
        }
    }

    /**
     * Helper method to safely deduct points during shop checkouts.
     */
    public void deductPoints(long points) {
        if (points > 0 && this.availablePoints >= points) {
            this.availablePoints -= points;
        } else if (this.availablePoints < points) {
            throw new IllegalArgumentException("Insufficient points allocation available.");
        }
    }
}