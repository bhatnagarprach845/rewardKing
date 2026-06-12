package com.bhatn.cashbackking.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse_del {
    private String cognitoId;
    private String email;
    private String upiId;
    private String message;
}