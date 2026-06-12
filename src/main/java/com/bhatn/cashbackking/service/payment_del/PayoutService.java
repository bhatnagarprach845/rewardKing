package com.bhatn.cashbackking.service.payment_del;

import com.bhatn.cashbackking.entity.User;
import com.bhatn.cashbackking.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    @Value("${razorpay.key.id}")
    private String apiKey;

    @Value("${razorpay.key.secret}")
    private String apiSecret;

    @Value("${razorpay.account.number}")
    private String razorpayAccountNumber;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository userRepository;

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    @PostConstruct
    public void init() {
        log.info("PayoutService loaded successfully. Environment Key Mask: {}",
                (apiKey != null && apiKey.length() > 8) ? apiKey.substring(0, 8) + "..." : "NULL");
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("CRITICAL CONFIGURATION ERROR: Razorpay API Key is missing from environment layout properties!");
        }
    }

    /**
     * Core operational trigger initiating cash reward transfers out to the destination layer.
     */
    public String triggerPayout(User user, BigDecimal amount) {
        // 1. Validation Check: Razorpay mechanical lower boundary is 100 paise (₹1.00)
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Payout processing failed: Minimum disbursement limit must be at least ₹1.00");
        }

        try {
            // Get existing or construct a validated Fund Account mapping
            String fundId = getOrCreateFundAccountId(user);

            // Create RazorpayX Payout payload model mapping
            JSONObject payoutRequest = new JSONObject();
            payoutRequest.put("account_number", String.valueOf(razorpayAccountNumber));
            payoutRequest.put("fund_account_id", fundId);
            payoutRequest.put("amount", amount.multiply(new BigDecimal(100)).intValue()); // Convert to Paise
            payoutRequest.put("currency", "INR");
            payoutRequest.put("mode", "UPI");
            payoutRequest.put("purpose", "payout");

            JSONObject response = postToRazorpay(BASE_URL + "/payouts", payoutRequest);
            String rzpPayoutId = response.getString("id");
            log.info("RazorpayX Payout pipeline executed successfully. Reference Allocation Token: {}", rzpPayoutId);

            return rzpPayoutId;
        } catch (HttpStatusCodeException e) {
            log.error("Razorpay Server API rejection stream: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Razorpay transaction fault: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Disbursement routing processing exception:", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Resolves localized caching mappings or executes remote account creations securely.
     */
    public String getOrCreateFundAccountId(User user) {
        try {
            // 1. Caching Optimization: If database already contains verified identifiers, reuse instantly
            if (user.getRazorpayFundAccountId() != null && !user.getRazorpayFundAccountId().isEmpty()) {
                log.info("Using cached Razorpay Fund Account ID context for user entity: {}", user.getName());
                return user.getRazorpayFundAccountId();
            }

            // ========================================================
            // REFACTORED: TEST-MODE COMPLIANT UPI VALIDATION GUARD
            // ========================================================
            log.info("Initiating structural validation guard for UPI ID: {}", user.getUpiId());

            if (apiKey != null && apiKey.startsWith("rzp_test_")) {
                // 🧪 TEST MODE RUNTIME (API validations are blocked by Razorpay in test sandboxes)
                log.info("DEBUG ENVIRONMENT: Razorpay Test Key detected. Applying local validation mocking patterns.");

                // Format check validation using Regex
                if (user.getUpiId() == null || !user.getUpiId().matches("^[\\w.-]+@[\\w.-]+$")) {
                    throw new IllegalArgumentException("Payout blocked: Invalid UPI ID format pattern structured in Test Mode.");
                }

                // Allow explicit error state simulation handling inside front-end modal views
                if (user.getUpiId().equalsIgnoreCase("fail@razorpay") || user.getUpiId().toLowerCase().contains("invalid")) {
                    throw new IllegalArgumentException("Payout blocked (Sandbox Mock): The UPI ID '" + user.getUpiId() + "' is simulated as unauthentic.");
                }

                log.info("UPI Sandbox validation bypass checklist: complete.");

            } else {
                // 🚀 LIVE PRODUCTION MODE RUNTIME (Only executed when live production keys are active)
                log.info("PRODUCTION ACTIVE: Executing synchronous bank lookup validation via RazorpayX routing...");

                JSONObject validationReq = new JSONObject();
                validationReq.put("account_number", razorpayAccountNumber);
                validationReq.put("vpa", user.getUpiId());

                try {
                    // Direct RazorpayX Payouts Validation Endpoint Framework
                    String validationUrl = "https://api.razorpay.com/v1/fund_accounts/validations";
                    JSONObject validationRes = postToRazorpay(validationUrl, validationReq);

                    JSONObject results = validationRes.optJSONObject("results");
                    boolean isValid = false;

                    if (results != null && results.has("vpa")) {
                        JSONObject vpaObj = results.getJSONObject("vpa");
                        isValid = vpaObj.optBoolean("valid", false);
                    } else {
                        isValid = validationRes.optBoolean("valid", false);
                    }

                    if (!isValid) {
                        throw new IllegalArgumentException("Payout blocked: The UPI ID '" + user.getUpiId() + "' could not be verified as an active account.");
                    }

                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("CRITICAL: Connection breakdown linking to live validation endpoints", e);
                    throw new IllegalArgumentException("Payment routing verification service is temporarily unreachable. Try again shortly.");
                }
            }
            // ========================================================

            // STEP 2: Create a Contact (Guaranteed non-null parameters via updated controller layout protection rules)
            log.info("Creating fresh Razorpay Contact profile entity row for: {}", user.getName());
            JSONObject contactReq = new JSONObject();
            contactReq.put("name", user.getName());
            contactReq.put("email", user.getEmail());
            contactReq.put("type", "customer");

            JSONObject contactRes = postToRazorpay(BASE_URL + "/contacts", contactReq);
            String contactId = contactRes.getString("id");

            // STEP 3: Create a Fund Account (Link UPI)
            log.info("Linking virtual payment address configuration to backend contact record mapping...");
            JSONObject faReq = new JSONObject();
            faReq.put("contact_id", contactId);
            faReq.put("account_type", "vpa");
            faReq.put("vpa", new JSONObject().put("address", user.getUpiId()));

            JSONObject faRes = postToRazorpay(BASE_URL + "/fund_accounts", faReq);
            String fundAccountId = faRes.getString("id");

            // STEP 4: Store relational parameters permanently inside persistent storage tiers
            user.setRazorpayContactId(contactId);
            user.setRazorpayFundAccountId(fundAccountId);

            log.info("Razorpay elements linked successfully. Contact: {}, Fund ID: {}", contactId, fundAccountId);
            return fundAccountId;

        } catch (IllegalArgumentException e) {
            throw e; // Bubble structural validation failures up to controller layer directly
        } catch (Exception e) {
            log.error("Handoff registration processing collapse tracking fault:", e);
            throw new RuntimeException("Failed to register UPI mapping criteria with Razorpay gateway: " + e.getMessage());
        }
    }

    /**
     * Standard internal transactional HTTP execution handler abstracting basic authentication configurations safely.
     */
    private JSONObject postToRazorpay(String url, JSONObject payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(apiKey, apiSecret);

        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return new JSONObject(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            log.error("SECURITY CRITICAL: Razorpay API rejected incoming request authentication keys. Check cloud environment properties variables.");
            throw new RuntimeException("CRITICAL: Credentials rejected by external service provider configuration layers.");
        } catch (HttpStatusCodeException e) {
            log.error("Http communications processing breakdown. Status Code: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected network failure accessing endpoints layer mapping context:", e);
            throw new RuntimeException("External communication transport layout error: " + e.getMessage());
        }
    }
}