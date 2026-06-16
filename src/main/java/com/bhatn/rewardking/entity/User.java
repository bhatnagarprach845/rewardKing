package com.bhatn.rewardking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String cognitoId; // Unique ID from AWS Cognito

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String name;



}