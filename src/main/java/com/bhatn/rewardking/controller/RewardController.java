package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.entity.RewardTransaction;
import com.bhatn.rewardking.entity.RewardTransaction.TransactionStatus;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.service.RewardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RewardController {

    private final RewardService rewardService;
    private final RewardTransactionRepository transactionRepository;

    /**
     * Fetch user's current points balance and historical points ledger.
     */
    @GetMapping("/payout-status")
    public ResponseEntity<?> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.error("Prachi Security :: Request dropped due to unauthenticated JWT context.");
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized token payload."));
        }

        String userId = extractUserId(jwt);
        log.info("Prachi Controller :: Fetching dashboard ledger details for database user_id: '{}'", userId);

        try {
            PayoutStatusResponse dashboardData = rewardService.getUserPointsDashboard(userId);
            return ResponseEntity.ok().body(dashboardData);
        } catch (IllegalArgumentException e) {
            log.warn("Prachi Controller :: Dashboard state error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createMapBody("error", e.getMessage()));
        }
    }

    /**
     * Bulk points redemption checkout processor for shopping cart line items.
     */
    @PostMapping("/redeem-points")
    public ResponseEntity<?> redeemPoints(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RedeemPointsRequest request) {

        if (jwt == null) {
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized token payload."));
        }

        String userId = extractUserId(jwt);
        log.info("Prachi Controller :: Processing bulk cart checkout for database user_id: '{}'", userId);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(createMapBody("error", "Cannot process an empty checkout cart basket."));
        }

        try {
            for (RedeemPointsRequest.CartItemDTO item : request.getItems()) {
                long totalItemCost = item.getPointsCost() * item.getQuantity();
                log.info("Prachi Controller :: Processing Item ID: {}, Quantity: {}, Aggregated Cost: {}",
                        item.getItemId(), item.getQuantity(), totalItemCost);

                rewardService.processPointsRedemption(userId, item.getItemId(), totalItemCost);
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Cart checked out and processed successfully.");
            responseBody.put("status", "SUCCESS");
            return ResponseEntity.ok().body(responseBody);

        } catch (IllegalArgumentException e) {
            log.warn("Prachi Controller :: Points processing failure: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createMapBody("error", e.getMessage()));
        }
    }

    /**
     * 🚀 ROUTE ALIGNMENT FIX: Handle direct order/payout validations securely
     */
    @PostMapping("/payouts/approve/{transactionId}")
    public ResponseEntity<?> approvePayout(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long transactionId) {

        if (jwt == null) {
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized access signature token context."));
        }

        log.info("Prachi Admin :: Confirming authorization for transaction ID execution state: {}", transactionId);

        try {
            RewardTransaction tx = transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction record not found inside history database tables."));

            // Convert state to COMPLETED or APPROVED
            tx.setStatus(TransactionStatus.COMPLETED);
            tx.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(tx);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("id", transactionId);
            responseBody.put("status", "COMPLETED");
            responseBody.put("message", "Item checkout authorization processed cleanly.");
            return ResponseEntity.ok().body(responseBody);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createMapBody("error", e.getMessage()));
        }
    }

    private String extractUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        if (userId == null) {
            userId = jwt.getClaimAsString("username");
        }
        return userId;
    }

    private Map<String, String> createMapBody(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
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