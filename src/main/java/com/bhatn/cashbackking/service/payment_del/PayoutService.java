package com.bhatn.cashbackking.service.payment_del;

import com.bhatn.cashbackking.entity.User;
import com.bhatn.cashbackking.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PayoutService {

    @Value("${razorpay.key.id}")
    private String apiKey;

    @Value("${razorpay.key.secret}")
    private String apiSecret;

    @Value("${razorpay.account.number}")
    private String razorpayAccountNumber;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository userRepository; // Needed to save the mapping

    private static final String BASE_URL = "https://api.razorpay.com/v1";

    @PostConstruct
    public void init() {
        System.out.println("DEBUG: PayoutService loaded with Key: " + apiKey);
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("ERROR: API Key is NOT being read from application.properties!");
        }
    }

   /* @Override
    public String initiateUpiPayout(User user, BigDecimal amount) {
        try {
            String url = "https://api.razorpay.com/v1/payouts";
            // GET THE REAL FUND ACCOUNT ID
            String realFundAccountId = getOrCreateFundAccountId(user.getUpiId(), userId);
            JSONObject payoutRequest = new JSONObject();
            payoutRequest.put("account_number", "7878780080316316"); // Your RazorpayX Account
            payoutRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // Paise
            payoutRequest.put("currency", "INR");
            payoutRequest.put("mode", "UPI");
            payoutRequest.put("purpose", "payout");
            payoutRequest.put("fund_account_id", realFundAccountId); // Linked to the user's UPI

            // Basic Auth Header
            String auth = apiKey + ":" + apiSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> entity = new HttpEntity<>(payoutRequest.toString(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                return jsonResponse.getString("id");
            } else {
                throw new RuntimeException("Failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            throw new RuntimeException("Razorpay Payout Failed: " + e.getMessage());
        }
    }*/

    public String triggerPayout(User user, BigDecimal amount) {
        // 1. Validation Check: Razorpay minimum is 100 paise (₹1.00)
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new RuntimeException("Payout failed: Minimum amount must be at least ₹1.00");
        }
        try {
            // Step 1 & 2: Get or Create the mapping
            String fundId = getOrCreateFundAccountId(user);

            // Step 3: Create Payout Request
            JSONObject payoutRequest = new JSONObject();
            payoutRequest.put("account_number", String.valueOf(razorpayAccountNumber));
            payoutRequest.put("fund_account_id", fundId);
            payoutRequest.put("amount", amount.multiply(new BigDecimal(100)).intValue()); // Paise
            payoutRequest.put("currency", "INR");
            payoutRequest.put("mode", "UPI");
            payoutRequest.put("purpose", "payout");

            JSONObject response = postToRazorpay(BASE_URL + "/payouts", payoutRequest);
            System.out.println("Payout initiated. ID: " + response.getString("id"));
            String rzpPayoutId = response.getString("id");
            System.out.println("Payout initiated. ID: " + rzpPayoutId);

            return rzpPayoutId; // Return the ID to be saved in the database
        } catch (HttpStatusCodeException e) {
            System.err.println("Razorpay Error Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Razorpay says: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
    public String getOrCreateFundAccountId(User user) {
        try {
            // 1. Check if we already have the ID saved in our DB
            if (user.getRazorpayFundAccountId() != null && !user.getRazorpayFundAccountId().isEmpty()) {
                System.out.println("Using existing Fund Account ID for: " + user.getName());
                return user.getRazorpayFundAccountId();
            }


            // STEP 1: Create a Contact
            JSONObject contactReq = new JSONObject();
            contactReq.put("name", user.getName());
            contactReq.put("email", user.getEmail());
            contactReq.put("type", "customer");

            JSONObject contactRes = postToRazorpay(BASE_URL + "/contacts", contactReq);
            String contactId = contactRes.getString("id");

            // STEP 2: Create a Fund Account (Link UPI)
            JSONObject faReq = new JSONObject();
            faReq.put("contact_id", contactId);
            faReq.put("account_type", "vpa");
            faReq.put("vpa", new JSONObject().put("address", user.getUpiId()));

            JSONObject faRes = postToRazorpay(BASE_URL + "/fund_accounts", faReq);
            String fundAccountId = faRes.getString("id");


            // STEP 3: Save the mapping to your database!
            user.setRazorpayContactId(contactId);
            user.setRazorpayFundAccountId(fundAccountId);
            userRepository.save(user);

            return fundAccountId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to register UPI with Razorpay: " + e.getMessage());
        }
    }
    private JSONObject postToRazorpay(String url, JSONObject payload) {
        // 1. Setup Headers with Basic Auth
        // Use the Spring-provided setBasicAuth method to avoid manual Base64 errors
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(apiKey, apiSecret); // Much safer than manual encoding

        // 2. Execute Request
        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return new JSONObject(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("CRITICAL: Razorpay rejected your Key ID/Secret. Check application.properties.");
        } catch (Exception e) {
            throw new RuntimeException("Razorpay API Error: " + e.getMessage());
        }
    }

   /* @Override
    public String initiateUpiPayout(String upiId, BigDecimal amount, String userId) {
        return null;
    }*/
}