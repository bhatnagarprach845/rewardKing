package com.bhatn.rewardking.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PayoutDTO {
    private String userId;
    private Long id;
    private String userName;

    // FIX: Switched from BigDecimal to long to process whole points
    private long amountAwarded;

    private LocalDateTime processedAt;
    private String status;
}