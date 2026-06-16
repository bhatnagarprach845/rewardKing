package com.bhatn.rewardking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String upiId; // User's primary/active VPA for the current payout processing runtime

    @Column(length = 100)
    private String selectedUpi; // Tracks the current active radio selection choice on the dashboard UI

    @Column(length = 50)
    private String razorpayContactId; // Required for RazorpayX Payouts

    // Field for Razorpay optimization
    private String razorpayFundAccountId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}