package com.bhatn.cashbackking.controller;

import com.bhatn.cashbackking.entity.CashbackTransaction;
import com.bhatn.cashbackking.entity.UserWallet;
import com.bhatn.cashbackking.repository.CashbackTransactionRepository;
import com.bhatn.cashbackking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final CashbackTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");

            // Navigate the JSON to get the payout entity
            JSONObject payoutEntity = event.getJSONObject("payload")
                    .getJSONObject("payout")
                    .getJSONObject("entity");

            String rzpPayoutId = payoutEntity.getString("id");

            // Find the transaction using the Razorpay Payout ID we saved during "Approve"
            Optional<CashbackTransaction> txOpt = transactionRepository.findByRazorpayPayoutId(rzpPayoutId);

            if (txOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Transaction not found");
            }

            CashbackTransaction tx = txOpt.get();

            if ("payout.processed".equals(eventType)) {
                // SUCCESS: Money hit user's bank
                tx.setStatus(CashbackTransaction.TransactionStatus.SETTLED);
                transactionRepository.save(tx);
            }
            else if ("payout.reversed".equals(eventType) || "payout.failed".equals(eventType)) {
                // FAILURE: Money didn't go through, REFUND the user's wallet
                Optional<UserWallet> walletOpt = walletRepository.findByUserId(tx.getUserId());
                if (walletOpt.isPresent()) {
                    UserWallet wallet = walletOpt.get();
                    wallet.setCurrentBalance(wallet.getCurrentBalance().add(tx.getAmountAwarded()));
                    walletRepository.save(wallet);
                }

                tx.setStatus(CashbackTransaction.TransactionStatus.FAILED);
                tx.setRemarks("Razorpay Error: " + eventType);
                transactionRepository.save(tx);
            }

            return ResponseEntity.ok("Webhook Processed");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}