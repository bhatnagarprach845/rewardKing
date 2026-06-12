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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class BillAnalyzer {

    @Autowired
    private TextractClient textractClient;

    // Compile regex patterns once to maximize serverless performance execution speeds
    private static final Pattern MGR_PATTERN = Pattern.compile("(?i)(MGR|MANAGER|STST|STORE|CASHIER|OP|HOST|TELLER|SERVED BY).*");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern CLEANUP_PATTERN = Pattern.compile("[^A-Z0-9\\s&'-]");

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

        // Tier 1: Extract Textract's default vendor prediction
        String rawMerchant = getField(summaryFields, "VENDOR_NAME");
        String totalStr = getField(summaryFields, "TOTAL");
        String dateStr = getField(summaryFields, "INVOICE_RECEIPT_DATE");

        // Tier 2 & 3: Fall back to top-of-page raw geometry if prediction is absent or contains layout noise
        if (rawMerchant == null || isNoise(rawMerchant)) {
            rawMerchant = extractTopBlockHeuristic(doc);
        }

        // Clean, sanitize and normalize the merchant identity generically
        String finalMerchantName = normalizeVendor(rawMerchant);

        // Map Line Items safely
        List<ExtractionResult.LineItemDTO> items = doc.lineItemGroups().stream()
                .flatMap(group -> group.lineItems().stream())
                .map(this::mapToLineItemDTO)
                .collect(Collectors.toList());

        // Defensive Date Parsing Check to protect from runtime crashes
        LocalDate purchaseDate = LocalDate.now();
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                purchaseDate = LocalDate.parse(dateStr.trim(), flexibleFormatter);
            } catch (Exception e) {
                purchaseDate = LocalDate.now();
            }
        }

        return ExtractionResult.builder()
                .merchantName(finalMerchantName)
                .totalAmount(parseAmount(totalStr))
                .purchaseDate(purchaseDate)
                .lineItems(items)
                .build();
    }

    private ExtractionResult.LineItemDTO mapToLineItemDTO(LineItemFields lineItem) {
        List<ExpenseField> fields = lineItem.lineItemExpenseFields();

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

    private boolean isNoise(String text) {
        String upper = text.toUpperCase();
        return upper.contains("MGR") || upper.contains("MANAGER") || upper.contains("CASHIER") || upper.length() < 2;
    }

    /**
     * FIXED TIER 3 HEURISTIC: Pulls from structural line item geometries across the document
     * block elements to safely read the physical text strings located at the absolute top of the page image.
     */
    private String extractTopBlockHeuristic(ExpenseDocument doc) {
        if (doc == null || doc.blocks() == null) {
            return "UNKNOWN_MERCHANT";
        }

        return doc.blocks().stream()
                // Null-safe filtering for block types, geometries, and bounding boxes
                .filter(b -> b != null &&
                        b.blockType() != null &&
                        BlockType.LINE.equals(b.blockType()) &&
                        b.geometry() != null &&
                        b.geometry().boundingBox() != null)
                // Limit bounds to the top 15% of the physical image asset height
                .filter(b -> b.geometry().boundingBox().top() < 0.15f)
                // Ensure the line contains actual text and letters
                .filter(b -> b.text() != null && b.text().replaceAll("[^A-Za-z]", "").length() > 2)
                // Find the element closest to the absolute top edge
                .min(Comparator.comparingDouble(b -> b.geometry().boundingBox().top()))
                .map(Block::text)
                .orElse("UNKNOWN_MERCHANT");
    }

    private String normalizeVendor(String rawVendor) {
        if (rawVendor == null || rawVendor.trim().isEmpty()) {
            return "UNKNOWN_MERCHANT";
        }

        String clean = rawVendor.toUpperCase().replaceAll("[\\r\\n]+", " ").trim();

        // 1. Remove manager titles, register numbers or checkout operators
        clean = MGR_PATTERN.matcher(clean).replaceAll("").trim();

        // 2. Remove standard store telephone sequence matches
        clean = PHONE_PATTERN.matcher(clean).replaceAll("").trim();

        // 3. Keep only standard business characters (Letters, Numbers, spaces, &, ', and -)
        clean = CLEANUP_PATTERN.matcher(clean).replaceAll("").trim();

        // 4. Collapse multiple spaces down to a single formatting space
        clean = clean.replaceAll("\\s+", " ");

        return clean.isEmpty() ? "UNKNOWN_MERCHANT" : clean.trim();
    }

    private Integer parseQuantity(String qty) {
        if (qty == null) return 1;
        try {
            String clean = qty.replaceAll("[^\\d]", "");
            return clean.isEmpty() ? 1 : Integer.parseInt(clean);
        } catch (Exception e) {
            return 1;
        }
    }

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
            return LocalDateTime.parse(dateStr.contains("T") ? dateStr : dateStr + "T00:00:00");
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}