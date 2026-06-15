package com.bhatn.rewardking.service;

import com.bhatn.rewardking.entity.RewardTransaction;
import com.bhatn.rewardking.entity.UserWallet;
import com.bhatn.rewardking.repository.RewardTransactionRepository;
import com.bhatn.rewardking.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final RewardTransactionRepository transactionRepository;

    @Transactional
    public ResponseEntity<String> redeemReward(String userId, BigDecimal amountToRedeem) {
        // 1. Lock the wallet for update
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // 2. Validation: Ensure they aren't withdrawing more than they have
        if (wallet.getCurrentBalance().compareTo(amountToRedeem) < 0) {
            throw new RuntimeException("Insufficient balance. You have ₹" + wallet.getCurrentBalance());
        }

        // 3. Create the Payout Transaction Record
        RewardTransaction payoutRecord = new RewardTransaction();
        payoutRecord.setUserId(userId);
        payoutRecord.setAmountAwarded(amountToRedeem.negate()); // Store as negative to show it's a withdrawal
        payoutRecord.setStatus(RewardTransaction.TransactionStatus.PENDING);
        //payoutRecord.setStatus(RewardTransaction.TransactionStatus.REDEEMED);
        payoutRecord.setProcessedAt(java.time.LocalDateTime.now());
        transactionRepository.save(payoutRecord);
        return ResponseEntity.ok("Request sent for Admin approval.");

       /* // 4. Update the Wallet Balance (Subtract ONLY the requested amount)
        BigDecimal newBalance = wallet.getCurrentBalance().subtract(amountToRedeem);
        wallet.setCurrentBalance(newBalance);
        wallet.setLastUpdated(java.time.LocalDateTime.now());
        walletRepository.save(wallet);

        // Note: We no longer need to loop through and "Settle" old COMPLETED transactions
        // because the currentBalance now correctly tracks the running total.*/
    }
    @Transactional
    public void confirmPayoutSuccess(String payoutId) {
        // 1. Find the transaction that matches the Razorpay Payout ID
        // Note: You may need to add findByPayoutId to your RewardTransactionRepository
        transactionRepository.findByPayoutId(payoutId).ifPresent(txn -> {
            txn.setStatus(RewardTransaction.TransactionStatus.SETTLED);
            txn.setProcessedAt(java.time.LocalDateTime.now());
            transactionRepository.save(txn);
        });
    }

    @Transactional
    public void handlePayoutFailure(String payoutId) {
        transactionRepository.findByPayoutId(payoutId).ifPresent(txn -> {
            // 1. Mark the transaction as FAILED so the user sees it
            txn.setStatus(RewardTransaction.TransactionStatus.FAILED);
            transactionRepository.save(txn);

            // 2. REFUND the money back to the wallet
            UserWallet wallet = walletRepository.findByUserIdForUpdate(txn.getUserId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found for refund"));

            // We use .abs() because withdrawals are stored as negative numbers
            BigDecimal refundAmount = txn.getAmountAwarded().abs();
            wallet.setCurrentBalance(wallet.getCurrentBalance().add(refundAmount));
            wallet.setLastUpdated(java.time.LocalDateTime.now());

            walletRepository.save(wallet);
        });
    }

    /*@Transactional
    public void initiateManualRedemption(String userId, BigDecimal amountToRedeem) {
        // 1. Fetch the wallet
        UserWallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // 2. Validation: check if user has enough balance
        if (wallet.getCurrentBalance().compareTo(amountToRedeem) < 0) {
            throw new RuntimeException("Insufficient balance for redemption");
        }

        // 3. Validation: Optional minimum withdrawal limit (e.g., ₹10)
        if (amountToRedeem.compareTo(new BigDecimal("10.00")) < 0) {
            throw new RuntimeException("Minimum redemption amount is ₹10");
        }

        // 4. Fetch User for UPI ID
        User user = use.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 5. Call Razorpay
        String externalPayoutId = paymentGateway.initiateUpiPayout(
                user.getUpiId(),
                amountToRedeem,
                userId
        );

        // 6. Deduct ONLY the requested amount
        wallet.setCurrentBalance(wallet.getCurrentBalance().subtract(amountToRedeem));
        walletRepo.save(wallet);

        // 7. Record the Payout Transaction
        txnRepo.save(Transaction.builder()
                .userId(userId)
                .amount(amountToRedeem)
                .type(Transaction.TransactionType.PAYOUT)
                .status(Transaction.TransactionStatus.PENDING)
                .payoutId(externalPayoutId)
                .build());
    }*/
    }