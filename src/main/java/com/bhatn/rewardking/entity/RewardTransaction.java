package com.bhatn.rewardking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Reward_transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RewardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Auto-incrementing primary key

    @Column(name = "receipt_id", nullable = true)
    private Long receiptId;

    @Column(nullable = false)
    private String userId;

    // FIX 1: Switched from BigDecimal to long to store clean loyalty points
    @Column(name = "points_amount", nullable = false)
    private long pointsAmount;

    // FIX 2: Added missing type field ("EARNED" or "REDEEMED")
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    // FIX 3: Added notes field to record item catalog identifiers
    @Column(length = 500)
    private String notes;

    @Column(unique = true)
    private String payoutId;

    @Column(length = 500)
    private String razorpayPayoutId;

    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        REDEEMED,
        SETTLED,
        FAILED,
        REVERSED,
        APPROVED,
        SHIPPED,
        DELIVERED
    }
}