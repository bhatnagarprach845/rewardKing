package com.bhatn.cashbackking.dto;

import com.bhatn.cashbackking.entity.CashbackTransaction;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PayoutStatusResponse {
    private BigDecimal currentBalance;
    private BigDecimal threshold; // Hardcoded to 30.00
    private String statusMessage; // e.g., "₹5.00 more needed for payout"
    private List<CashbackTransaction> recentTransactions;
    private String upiId;

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