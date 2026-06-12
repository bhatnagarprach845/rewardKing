package com.bhatn.cashbackking.dto;

import com.bhatn.cashbackking.entity.CashbackTransaction;
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

    // Ledger history
    private List<CashbackTransaction> recentTransactions;

    @Data
    @Builder
    public static class TransactionDTO {
        private Long id;
        private BigDecimal amount;
        private String type;
        private String status;
        private String date;
    }
}