package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.entity.User;
import com.bhatn.rewardking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody; // Added import
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Direct, clean data structure mapping for incoming payload strings
    public record UserSyncRequest(String email, String name) {}

    @PostMapping("/sync")
    public ResponseEntity<User> sync(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UserSyncRequest requestBody) { // 🚀 FIX: Accepting payload body here

        return ResponseEntity.ok(userService.syncUser(jwt, requestBody));
    }
}