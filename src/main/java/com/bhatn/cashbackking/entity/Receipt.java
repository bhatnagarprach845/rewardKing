package com.bhatn.cashbackking.entity;


import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "receipts")
@Data
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private String merchantName;

    // Add this field to fix the error
    private String merchantZipCode;
    private BigDecimal totalAmount;
    private LocalDate purchaseDate;
    private String s3Key;

    // The missing piece: Tracks the lifecycle of the receipt
    @Enumerated(EnumType.STRING)
    private ReceiptStatus status = ReceiptStatus.UPLOADED;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL)
    @JsonManagedReference // This side will be serialized
    private List<ReceiptItem> items = new ArrayList<>();

    private LocalDateTime createdAt;
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReceiptStatus.UPLOADED;
        }
    }

    public void addItem(ReceiptItem item) {
        items.add(item);
        item.setReceipt(this); // This links the item back to the receipt ID
    }

    @Override
    public String toString() {
        return "Receipt{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", merchantZipCode='" + merchantZipCode + '\'' +
                ", totalAmount=" + totalAmount +
                ", purchaseDate=" + purchaseDate +
                ", s3Key='" + s3Key + '\'' +
                ", status=" + status +
                ", items=" + items +
                ", createdAt=" + createdAt +
                '}';
    }
}