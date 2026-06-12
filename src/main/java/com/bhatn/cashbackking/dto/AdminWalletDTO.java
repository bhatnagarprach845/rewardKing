package com.bhatn.cashbackking.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminWalletDTO {
    private String userId;      // The Cognito ID (for the link)
    private String fullName;    // The Name from the User entity
    private String email;
    private String upiId;
    private BigDecimal currentBalance;
    private LocalDateTime lastUpdated;
}