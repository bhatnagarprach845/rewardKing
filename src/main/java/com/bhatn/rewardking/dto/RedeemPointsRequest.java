package com.bhatn.rewardking.dto;

import lombok.Data;

@Data
public class RedeemPointsRequest {
    private String itemId;
    private long pointsCost;
}