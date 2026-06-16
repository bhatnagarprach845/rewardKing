package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.dto.RedeemPointsRequest;
import com.bhatn.rewardking.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Cross-Origin configuration for Amplify frontend mapping
public class RewardController {

    private final RewardService rewardService;

    /**
     * Fetch user's current points balance and historical points ledger.
     */
    @GetMapping("/payout-status")
    public ResponseEntity<PayoutStatusResponse> getPayoutStatus(@AuthenticationPrincipal Principal principal) {
        String username = principal.getName(); // Extracted securely from AWS Cognito JWT Token
        PayoutStatusResponse response = rewardService.getUserPointsDashboard(username);
        return ResponseEntity.ok(response);
    }

    /**
     * Process shop order, validate points availability, and deduct points balance.
     */
    @PostMapping("/redeem-points")
    public ResponseEntity<?> redeemPoints(
            @AuthenticationPrincipal Principal principal,
            @RequestBody RedeemPointsRequest request) {
        
        String username = principal.getName();
        
        try {
            rewardService.processPointsRedemption(username, request.getItemId(), request.getPointsCost());
            return ResponseEntity.ok().body("{\"message\": \"Order processed successfully.\"}");
        } catch (IllegalArgumentException e) {
            // Handles insufficient points or invalid items
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}