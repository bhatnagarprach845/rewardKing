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
        // 🛡️ Guard Block against invalid signatures
        if (jwt == null) {
            log.error("Prachi Security :: Request dropped due to unauthenticated JWT context.");
            return ResponseEntity.status(401).body(createMapBody("error", "Unauthorized token payload."));
        }

        // 🚀 Cascade claim parsing to safely extract user identity strings
        String username = extractUsername(jwt);
        log.info("Prachi Controller :: Fetching dashboard ledger details for verified user: '{}'", username);

        try {
            // Linked directly to your exact Service method name: getUserPointsDashboard
            PayoutStatusResponse dashboardData = rewardService.getUserPointsDashboard(username);
            return ResponseEntity.ok().body(dashboardData);
        } catch (IllegalArgumentException e) {
            log.warn("Prachi Controller :: Dashboard state error: {}", e.getMessage());
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

    /**
     * Process shop order, validate points availability, and deduct points balance.
     */
    @PostMapping("/redeem-points")
    public ResponseEntity<?> redeemPoints(
            @AuthenticationPrincipal Jwt jwt, // 🚀 1. Bind directly to the verified JWT container
            @RequestBody RedeemPointsRequest request) {

        // 🚀 2. STRICT SECURITY GUARD: If the token didn't validate, exit cleanly with a 401 Unauthorized
        if (jwt == null) {
            log.error("Prachi Security Alert :: Incoming JWT token could not be resolved or validated by Spring Security context!");
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Unauthorized: Invalid or missing security token context.");
            return ResponseEntity.status(401).body(errorResponse);
        }

        // 🚀 3. ROBUST CLAIM RESOLUTION: Fallback cascade strategy to safely read the user identity string
        String username = jwt.getClaimAsString("username"); // Try reading standard Cognito Access Token layout ("anilk")
        if (username == null) {
            username = jwt.getClaimAsString("sub"); // Fallback to the unique UUID string if username is blank
        }
        if (username == null) {
            username = jwt.getClaimAsString("client_id"); // Third backup fallback option
        }

        log.info("Prachi Controller :: Verified user identity: '{}'. Initiating points redemption request.", username);

        try {
            // Execute the balance modifications inside your service layer
            rewardService.processPointsRedemption(username, request.getItemId(), request.getPointsCost());

            // 🚀 4. AWS PROXY FIX: Return an object Map so Jackson maps headers cleanly to API Gateway, preventing 502s
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Order processed successfully.");
            responseBody.put("status", "SUCCESS");

            return ResponseEntity.ok().body(responseBody);

        } catch (IllegalArgumentException e) {
            log.warn("Prachi Controller :: Points processing business validation failure: {}", e.getMessage());
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}