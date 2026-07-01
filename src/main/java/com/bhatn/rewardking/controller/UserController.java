package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.entity.User;
import com.bhatn.rewardking.service.RewardService;
import com.bhatn.rewardking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final RewardService rewardService;

    // Direct, clean data structure mapping for incoming payload strings
    public record UserSyncRequest(String email, String name) {}

    @PostMapping("/sync")
    public ResponseEntity<User> sync(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UserSyncRequest requestBody) {
        return ResponseEntity.ok(userService.syncUser(jwt, requestBody));
    }

    /**
     * 🚀 MOVED HERE: User profiles cleanly separated under user endpoints namespace mapping rules
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));
        return ResponseEntity.ok().body(rewardService.getUserProfileData(extractUserId(jwt)));
    }

    @PostMapping("/profile/update")
    public ResponseEntity<?> updateUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> payload) {
        if (jwt == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));
        rewardService.updateUserProfileData(extractUserId(jwt), payload);
        return ResponseEntity.ok().body(Map.of("message", "Profile records synchronized successfully!"));
    }

    private String extractUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        return userId != null ? userId : jwt.getClaimAsString("username");
    }
}