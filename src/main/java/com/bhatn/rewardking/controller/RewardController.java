package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.service.RewardService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Cross-Origin configuration for Amplify frontend mapping
public class RewardController {

    private final RewardService rewardService;

    /**
     * Fetch user's current points balance and historical points ledger.
     */
    @GetMapping("/payout-status")
    public ResponseEntity<?> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.error("Prachi Security :: Request dropped due to unauthenticated JWT context.");
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized token payload."));
        }

        // 🚀 Call the fixed utility method to extract the 'sub' UUID
        String userId = extractUserId(jwt);
        log.info("Prachi Controller :: Fetching dashboard ledger details for database user_id: '{}'", userId);

        try {
            // Passes the UUID string (e.g. "41fbb590-...") which matches your DB row perfectly!
            PayoutStatusResponse dashboardData = rewardService.getUserPointsDashboard(userId);
            return ResponseEntity.ok().body(dashboardData);
        } catch (IllegalArgumentException e) {
            log.warn("Prachi Controller :: Dashboard state error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createMapBody("error", e.getMessage()));
        }
    }

    @PostMapping("/redeem-points")
    public ResponseEntity<?> redeemPoints(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RedeemPointsRequest request) {

        if (jwt == null) {
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized token payload."));
        }

        // 🚀 Call the fixed utility method here as well
        String userId = extractUserId(jwt);
        log.info("Prachi Controller :: Processing point checkout for database user_id: '{}'", userId);

        try {
            rewardService.processPointsRedemption(userId, request.getItemId(), request.getPointsCost());

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Order processed successfully.");
            responseBody.put("status", "SUCCESS");
            return ResponseEntity.ok().body(responseBody);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(createMapBody("error", e.getMessage()));
        }
    }
    /**
     * Utility parser to securely read identity strings from Cognito tokens.
     */
    private String extractUsername(Jwt jwt) {
        String username = jwt.getClaimAsString("username"); // Matches Access Token payload
        if (username == null) {
            username = jwt.getClaimAsString("sub"); // Fallback to user UUID string string mapping
        }
        return username;
    }

    /**
     * Utility parser to securely extract the Cognito UUID string (sub)
     * to align with database primary keys.
     */
    private String extractUserId(Jwt jwt) {
        // 🚀 CRITICAL FIX: Prioritize 'sub' because your tables use the Cognito UUID as user_id/cognito_id
        String userId = jwt.getClaimAsString("sub");

        if (userId == null) {
            userId = jwt.getClaimAsString("username"); // Backup fallback
        }
        return userId;
    }

    /**
     * Utility mapper to format standard JSON response error wrappers.
     */
    private Map<String, String> createMapBody(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * DTO payload matching your front-end Axios post data structure precisely.
     */
    @Data
    public static class RedeemPointsRequest {
        private String itemId;
        private long pointsCost;
    }


}