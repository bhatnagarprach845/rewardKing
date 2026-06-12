package com.bhatn.cashbackking.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PayoutDTO {
    private String userId;
    private Long id;
    private String userName; // This is what we're adding
    private BigDecimal amountAwarded;
    private LocalDateTime processedAt;
    private String status;
}