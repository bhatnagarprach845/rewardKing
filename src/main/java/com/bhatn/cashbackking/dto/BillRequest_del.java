package com.bhatn.cashbackking.dto;

import lombok.Data;

@Data
public class BillRequest_del {
    private String s3Key; // The path to the image in your S3 bucket
}
