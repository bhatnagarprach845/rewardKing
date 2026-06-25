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

    // Fixed: Generic type matches internal DTO specifications smoothly
    private List<TransactionDTO> recentTransactions;

    @Data
    @Builder
    public static class TransactionDTO {
        private String id;
        private long amount;          // Amount of loyalty points
        private long amountAwarded;   // 🚀 Added: Needed for React amount checking checks
        private String type;          // "EARNED" or "REDEEMED"
        private String date;          // yyyy-MM-dd
        private String processedAt;   // 🚀 Added: Full ISO date string for user activity layout grids
        private String status;        // "COMPLETED" or "PENDING"
        private String notes;         // 🚀 Added: Stores the merchandise description details
    }
}