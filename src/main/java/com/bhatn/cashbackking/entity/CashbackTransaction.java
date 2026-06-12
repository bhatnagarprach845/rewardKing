package com.bhatn.cashbackking.entity;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashback_transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CashbackTransaction {

    @Column(unique = true)
    private String payoutId; // Store the "pout_..." ID from Razorpay
    @Override
    public String toString() {
        return "CashbackTransaction{" +
                "id=" + id +
                ", receiptId=" + receiptId +
                ", userId='" + userId + '\'' +
                ", amountAwarded=" + amountAwarded +
                ", processedAt=" + processedAt +
                ", status=" + status +
                ", remarks='" + remarks + '\'' +
                '}';
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_id", nullable = true)
    private Long receiptId; // Links back to the specific receipt that earned this

    @Column(nullable = false)
    private String userId; // The user who earned the reward

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amountAwarded; // The specific amount (e.g., ₹2.00)

    @Column(nullable = false)
    private LocalDateTime processedAt; // When the cashback was credited

    @Enumerated(EnumType.STRING)
    private TransactionStatus status; // COMPLETED, REVERSED (for fraud), PAID_OUT

    @Column(length = 500)
    private String remarks; // e.g., "Bonus for first upload" or "Standard 1% cashback"
    @Column(length = 500)
    private String razorpayPayoutId; // e.g., "Bonus for first upload" or "Standard 1% cashback"

    public enum TransactionStatus {
        PENDING, // User requested, Admin hasn't seen it yet
        COMPLETED,  // Bill uploaded successfully
        REDEEMED,   // User requested payout (Pending)
        SETTLED,    // // Webhook confirmed success - Money hit user's bank account (Success)
        FAILED,      // // Webhook confirmed failure - Bank transfer failed (Money refunded to wallet)   // This amount was part of a ₹30+ payout
        REVERSED,
        APPROVED // Admin clicked 'Approve', sent to Razorpay
    }
}