package com.bhatn.rewardking.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminWalletDTO {
    private String userId;      // The Cognito ID
    private String fullName;    // The Name from the User entity
    private String email;

    // FIX 1: Switched from BigDecimal to long to pass points instead of cash decimals
    private long currentBalance;

    private LocalDateTime lastUpdated;
}