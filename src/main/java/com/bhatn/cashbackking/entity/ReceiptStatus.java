package com.bhatn.cashbackking.entity;


import lombok.Getter;

@Getter
public enum ReceiptStatus {

    /**
     * Initial state when user uploads the image.
     */
    UPLOADED("Receipt received and awaiting OCR processing."),

    /**
     * Currently being processed by AWS Textract.
     */
    PROCESSING("Extracting item data and validating merchant."),

    /**
     * Successfully processed; cashback has been credited to the wallet.
     */
    PROCESSED("Cashback credited successfully."),

    /**
     * Rejected due to duplication (already uploaded) or poor image quality.
     */
    REJECTED("Receipt rejected. This may be a duplicate or unreadable."),

    /**
     * System error during OCR or database save; requires manual or auto-retry.
     */
    FAILED("Processing failed due to a system error. Please try again."),
    FLAGGED_FOR_REVIEW("Receipt flagged for dynamic administrative fraud review.");


    private final String description;

    ReceiptStatus(String description) {
        this.description = description;
    }
}