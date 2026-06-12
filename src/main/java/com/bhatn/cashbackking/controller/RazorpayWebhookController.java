package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.entity.CashbackTransaction;
import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.CashbackTransactionRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final CashbackTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    // The colon at the end means "default to empty string if env var is not set".
    // This allows the app to start locally without the webhook secret configured.
    // In production, RAZORPAY_WEBHOOK_SECRET must always be set.
    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    /**
     * Handles all incoming Razorpay payout webhook events.
     *
     * FIX (Critical): This is now the SINGLE place where wallet balances are
     * deducted after a payout. Previously AdminController zeroed the wallet
     * immediately on payout initiation, before Razorpay confirmed success.
     * That meant a Razorpay failure would silently wipe the user's balance.
     *
     * Flow:
     *   - "payout.processed" → deduct the exact payout amount from the wallet
     *   - "payout.reversed" / "payout.failed" → refund the amount back
     *
     * FIX (Critical): Added HMAC-SHA256 signature verification so that only
     * genuine Razorpay events can mutate wallet balances.
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // Verify the webhook signature before processing anything
        if (!isSignatureValid(payload, signature)) {
            log.warn("Webhook signature verification failed. Rejecting request.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");

            JSONObject payoutEntity = event.getJSONObject("payload")
                    .getJSONObject("payout")
                    .getJSONObject("entity");

            String rzpPayoutId = payoutEntity.getString("id");

            Optional<CashbackTransaction> txOpt = transactionRepository.findByRazorpayPayoutId(rzpPayoutId);

            if (txOpt.isEmpty()) {
                log.warn("Webhook received for unknown Razorpay payout ID: {}", rzpPayoutId);
                // Return 200 so Razorpay doesn't keep retrying for IDs we didn't create
                return ResponseEntity.ok("Transaction not tracked — acknowledged");
            }

            CashbackTransaction tx = txOpt.get();

            if ("payout.processed".equals(eventType)) {
                // SUCCESS: Razorpay confirmed money hit the user's bank.
                // Deduct the wallet balance now (safe, confirmed transfer).
                deductWallet(tx);
                tx.setStatus(CashbackTransaction.TransactionStatus.SETTLED);
                log.info("Payout {} confirmed. Wallet debited for user {}.", rzpPayoutId, tx.getUserId());

            } else if ("payout.reversed".equals(eventType) || "payout.failed".equals(eventType)) {
                // FAILURE: Money didn't go through — refund the user's wallet.
                // amountAwarded is stored as negative (withdrawal), so abs() gives the refund amount.
                refundWallet(tx);
                tx.setStatus(CashbackTransaction.TransactionStatus.FAILED);
                tx.setRemarks("Razorpay error event: " + eventType);
                log.warn("Payout {} failed/reversed. Wallet refunded for user {}.", rzpPayoutId, tx.getUserId());
            }

            tx.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(tx);

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deducts the payout amount from the user's wallet.
     * Uses a pessimistic write lock (via findByUserIdForUpdate) to prevent
     * race conditions with concurrent wallet operations.
     */
    private void deductWallet(CashbackTransaction tx) {
        UserWallet wallet = walletRepository.findByUserIdForUpdate(tx.getUserId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + tx.getUserId()));

        // amountAwarded is stored as a negative number for withdrawals; abs() gives the deduction
        BigDecimal deduction = tx.getAmountAwarded().abs();

        if (wallet.getCurrentBalance().compareTo(deduction) < 0) {
            log.error("Insufficient wallet balance for deduction on confirmed payout {}. " +
                            "Balance: {}, Deduction: {}", tx.getRazorpayPayoutId(),
                    wallet.getCurrentBalance(), deduction);
            // Don't throw — the payout already succeeded. Log for manual reconciliation.
            return;
        }

        wallet.setCurrentBalance(wallet.getCurrentBalance().subtract(deduction));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    /**
     * Refunds a failed payout amount back to the user's wallet.
     */
    private void refundWallet(CashbackTransaction tx) {
        Optional<UserWallet> walletOpt = walletRepository.findByUserIdForUpdate(tx.getUserId());
        if (walletOpt.isEmpty()) {
            log.error("Cannot refund — wallet not found for user: {}", tx.getUserId());
            return;
        }
        UserWallet wallet = walletOpt.get();
        BigDecimal refundAmount = tx.getAmountAwarded().abs();
        wallet.setCurrentBalance(wallet.getCurrentBalance().add(refundAmount));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);
    }

    /**
     * Verifies the HMAC-SHA256 signature that Razorpay sends with every webhook.
     * Razorpay signs the raw request body with the webhook secret.
     *
     * If RAZORPAY_WEBHOOK_SECRET is not configured (e.g. local dev before the
     * Razorpay webhook is set up), verification is skipped with a warning.
     * NEVER leave the secret unset in production.
     */
    private boolean isSignatureValid(String payload, String signature) {
        // Dev/test bypass: if no secret is configured, skip verification but warn loudly
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("RAZORPAY_WEBHOOK_SECRET is not set — skipping signature verification. " +
                    "Set this variable before going to production.");
            return true;
        }

        if (signature == null || signature.isBlank()) {
            log.warn("No X-Razorpay-Signature header present in webhook request.");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(rawHmac);
            return computedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error during webhook signature verification", e);
            return false;
        }
    }
}
