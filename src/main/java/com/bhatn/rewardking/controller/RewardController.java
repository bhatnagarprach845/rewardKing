package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.service.RewardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RewardController {

    private final RewardService rewardService;

    @GetMapping("/store/items")
    public ResponseEntity<?> getStoreCatalog() {
        return ResponseEntity.ok(rewardService.getAllStoreItems());
    }

    @PostMapping("/redeem-points")
    public ResponseEntity<?> redeemPoints(@AuthenticationPrincipal Jwt jwt, @RequestBody RedeemPointsRequest request) {
        if (jwt == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized token."));
        String userId = extractUserId(jwt);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot process an empty cart."));
        }

        try {
            rewardService.processBulkCartRedemption(userId, request.getItems());
            return ResponseEntity.ok(Map.of("message", "Cart checked out atomically.", "status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/payouts/update-status/{transactionId}")
    public ResponseEntity<?> updateOrderStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long transactionId,
            @RequestParam String newStatus,
            @RequestParam(required = false) String trackingNumber) {
        if (jwt == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized."));

        try {
            rewardService.updateOrderStatusWithTracking(transactionId, newStatus, trackingNumber);
            return ResponseEntity.ok(Map.of("status", "SUCCESS"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/payout-status")
    public ResponseEntity<?> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(rewardService.getUserPointsDashboard(extractUserId(jwt)));
    }

    private String extractUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        return userId != null ? userId : jwt.getClaimAsString("username");
    }

    @Data
    public static class RedeemPointsRequest {
        private List<CartItemDTO> items;
        @Data
        public static class CartItemDTO {
            private String itemId;
            private int quantity;
            private long pointsCost;
        }
    }
}