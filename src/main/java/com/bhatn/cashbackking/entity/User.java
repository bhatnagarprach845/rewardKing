package com.bhatn.cashbackking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String cognitoId; // Unique ID from AWS Cognito

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 100)
    private String upiId; // User's VPA (e.g., name@okaxis)

    @Column(length = 50)
    private String razorpayContactId; // Required for RazorpayX Payouts

    // Field for Razorpay optimization we discussed earlier
    private String razorpayFundAccountId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}