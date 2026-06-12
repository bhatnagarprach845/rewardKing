package com.bhatn.cashbackking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Stores the collection of multiple verified UPI addresses for a single user profile.
     * FetchType.EAGER ensures the list is populated immediately when retrieving the user context.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_upi_addresses",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "upi_address", length = 100)
    @Builder.Default
    private List<String> upiIds = new ArrayList<>();

    @Column(length = 50)
    private String razorpayContactId; // Required for RazorpayX Payouts

    // Field for Razorpay optimization
    private String razorpayFundAccountId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        // Initialize collection on instantiation if null
        if (this.upiIds == null) {
            this.upiIds = new ArrayList<>();
        }
    }
}