package com.bhatn.rewardking.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PayoutStatusResponse {
    private String name;
    private String email;
    private long currentBalance;
    private long threshold;
    private String statusMessage;

    // FIX 1: Updated generic type to TransactionDTO to align with Controller expectations
    private List<TransactionDTO> recentTransactions;

    // FIX 2: Renamed inner class name to uppercase DTO to stop case-sensitive compilation drops
    @Data
    @Builder
    public static class TransactionDTO {
        private String id;
        private long amount;       // Amount of loyalty points
        private String type;       // "EARNED" or "REDEEMED"
        private String date;       // yyyy-MM-dd
        private String status;     // "COMPLETED" or "PENDING"
    }
}