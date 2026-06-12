package com.bhatn.cashbackking.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ExtractionResult {
    private String merchantName;
    private BigDecimal totalAmount;
    private LocalDate purchaseDate;
    private String rawText; // Useful for deep analytics later

    // NEW: List of individual items from the bill
    private List<LineItemDTO> lineItems;

    @Data
    @Builder
    public static class LineItemDTO {
        private String description;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}