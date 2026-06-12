package com.bhatn.cashbackking.service.ocr;

import com.bhatn.cashbackking.dto.ExtractionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BillAnalyzer {
    @Autowired
    private TextractClient textractClient;

    public ExtractionResult analyze(byte[] imageBytes) {
        AnalyzeExpenseRequest request = AnalyzeExpenseRequest.builder()
                .document(Document.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build())
                .build();

        AnalyzeExpenseResponse response = textractClient.analyzeExpense(request);
        return mapResponse(response);
    }

    private ExtractionResult mapResponse(AnalyzeExpenseResponse response) {
        DateTimeFormatter flexibleFormatter = new DateTimeFormatterBuilder()
                .appendPattern("[MM/dd/yyyy]") // Try 4-digit year first
                .appendPattern("[MM/dd/yy]")   // Then try 2-digit year
                .appendPattern("[yyyy-MM-dd]") // Standard ISO
                .toFormatter();

        if (response.expenseDocuments().isEmpty()) {
            return ExtractionResult.builder().totalAmount(BigDecimal.ZERO).lineItems(new ArrayList<>()).build();
        }

        ExpenseDocument doc = response.expenseDocuments().get(0);
        List<ExpenseField> summaryFields = doc.summaryFields();

        String merchant = getField(summaryFields, "VENDOR_NAME");
        String totalStr = getField(summaryFields, "TOTAL");
        String dateStr = getField(summaryFields, "INVOICE_RECEIPT_DATE");

        // Map Line Items safely
        List<ExtractionResult.LineItemDTO> items = doc.lineItemGroups().stream()
                .flatMap(group -> group.lineItems().stream())
                .map(this::mapToLineItemDTO)
                .collect(Collectors.toList());

        return ExtractionResult.builder()
                .merchantName(merchant != null ? merchant : "UNKNOWN")
                .totalAmount(parseAmount(totalStr))
                .purchaseDate(LocalDate.parse(dateStr, flexibleFormatter))
                .lineItems(items)
                .build();
    }

    private ExtractionResult.LineItemDTO mapToLineItemDTO(LineItemFields lineItem) {
        List<ExpenseField> fields = lineItem.lineItemExpenseFields();

        // Textract line item types: ITEM, PRICE, QUANTITY, UNIT_PRICE
        String description = getField(fields, "ITEM");
        String priceStr = getField(fields, "PRICE");
        String qtyStr = getField(fields, "QUANTITY");
        String unitPriceStr = getField(fields, "UNIT_PRICE");

        return ExtractionResult.LineItemDTO.builder()
                .description(description != null ? description : "Unknown Item")
                .price(parseAmount(priceStr))
                .quantity(parseQuantity(qtyStr))
                .unitPrice(parseAmount(unitPriceStr))
                .build();
    }

    private Integer parseQuantity(String qty) {
        if (qty == null) return 1;
        try {
            String clean = qty.replaceAll("[^\\d]", "");
            return clean.isEmpty() ? 1 : Integer.parseInt(clean);
        } catch (Exception e) {
            return 1;
        }
    } // FIXED: Added missing closing brace

    private String getField(List<ExpenseField> fields, String type) {
        return fields.stream()
                .filter(f -> f.type() != null && f.type().text().equals(type))
                .map(f -> f.valueDetection().text())
                .findFirst().orElse(null);
    }

    private BigDecimal parseAmount(String val) {
        if (val == null) return BigDecimal.ZERO;
        try {
            String clean = val.replaceAll("[^\\d.]", "");
            return clean.isEmpty() ? BigDecimal.ZERO : new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            // Textract often returns YYYY-MM-DD. We add time to satisfy LocalDateTime.
            return LocalDateTime.parse(dateStr.contains("T") ? dateStr : dateStr + "T00:00:00");
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}