package com.bhatn.cashbackking.service.payment_del;

import java.math.BigDecimal;

public interface PaymentGateway {
    /**
     * Initiates a UPI transfer to the user.
     * @return The external Payout ID from the provider (e.g., Razorpay Payout ID).
     */
    String initiateUpiPayout(String upiId, BigDecimal amount, String userId);
}