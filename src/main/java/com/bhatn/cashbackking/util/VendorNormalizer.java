package com.bhatn.cashbackking.util;

import java.util.regex.Pattern;

public class VendorNormalizer {

    // Patterns to strip common noise that pollutes OCR vendor extractions
    private static final Pattern MGR_PATTERN = Pattern.compile("(?i)(MGR|MANAGER|STST|STORE|CASHIER|OP|HOST|TELLER|SERVED BY).*");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern CLEANUP_PATTERN = Pattern.compile("[^A-Z0-9\\s&'-]");

    public static String normalize(String rawVendor) {
        if (rawVendor == null || rawVendor.trim().isEmpty()) {
            return "UNKNOWN_VENDOR";
        }

        // Convert to absolute uppercase and strip line breaks
        String clean = rawVendor.toUpperCase().replaceAll("[\\r\\n]+", " ").trim();

        // 1. Strip out manager names or cashier titles if Textract combined them
        clean = MGR_PATTERN.matcher(clean).replaceAll("").trim();

        // 2. Remove phone numbers if accidentally captured in the block
        clean = PHONE_PATTERN.matcher(clean).replaceAll("").trim();

        // 3. Keep only standard legal alphanumeric characters, spaces, and business punctuation (&, ', -)
        clean = CLEANUP_PATTERN.matcher(clean).replaceAll("").trim();

        // 4. Collapse multiple spaces down to a single space
        clean = clean.replaceAll("\\s+", " ");

        return clean.isEmpty() ? "UNKNOWN_VENDOR" : clean.trim();
    }
}