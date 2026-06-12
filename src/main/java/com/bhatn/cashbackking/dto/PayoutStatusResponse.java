package com.bhatn.cashbackking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PayoutStatusResponse {
    // Basic Profile Data required by the Profile Modal layout
    private String name;
    private String email;

    // Financial calculations
    private BigDecimal currentBalance;
    private BigDecimal threshold; // Hardcoded to 30.00
    private String statusMessage; // e.g., "₹5.00 more needed for payout"

    // Core Multi-UPI updates
    private String upiId; // Main/active fallback UPI string
    private List<String> upiIds; // Collection array of all registered UPI handles
    private String selectedUpi; // The target currently active via dashboard radio selection

    // Ledger history - UPDATED to use TransactionDTO instead of raw entity class
    private List<TransactionDTO> recentTransactions;

    @Data
    @Builder
    public static class TransactionDTO {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("amount") // 🧠 CRITICAL: Explicitly forces Jackson to output the key name as "amount"
        private BigDecimal amount;

        @JsonProperty("type")
        private String type;

        @JsonProperty("status")
        private String status;

        @JsonProperty("date")
        private String date;
    }
}