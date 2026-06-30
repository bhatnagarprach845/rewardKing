package com.bhatn.rewardking.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "store_items")
public class StoreItem {
    @Id
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "points_cost", nullable = false)
    private long pointsCost;

    @Column(name = "stock_level", nullable = false)
    private int stockLevel;

    @Column(name = "image_emoji")
    private String imageEmoji;
}