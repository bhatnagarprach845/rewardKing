package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.ExtractionResult;
import com.bhatn.rewardking.dto.PayoutStatusResponse;
import com.bhatn.rewardking.entity.*;
import com.bhatn.rewardking.repository.ReceiptRepository;
import com.bhatn.rewardking.service.ReceiptProcessor;
import com.bhatn.rewardking.service.RewardService;
import com.bhatn.rewardking.service.ocr.BillAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class ReceiptController {

    private final ReceiptProcessor receiptProcessor;
    private final ReceiptRepository receiptRepository;
    private final RewardService rewardService;
    private final BillAnalyzer billAnalyzer;
    private final String bucketName;

    public ReceiptController(
            ReceiptProcessor receiptProcessor,
            ReceiptRepository receiptRepository,
            RewardService rewardService,
            BillAnalyzer billAnalyzer,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.receiptProcessor = receiptProcessor;
        this.receiptRepository = receiptRepository;
        this.rewardService = rewardService;
        this.billAnalyzer = billAnalyzer;
        this.bucketName = bucketName;
    }

    /**
     * 1. Production S3 Processing Pathway
     */
    @PostMapping("/process-s3")
    public ResponseEntity<Map<String, Object>> processS3Receipt(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("sub");
        String s3Key = request.get("s3Key");

        log.info("Processing S3 snapshot validation context for user: {}", username);

        Receipt receipt = new Receipt();
        receipt.setUserId(username);
        receipt.setS3Key(s3Key);
        receipt.setStatus(ReceiptStatus.PROCESSING);

        receipt = receiptRepository.saveAndFlush(receipt);
        receiptProcessor.processRewardAsync(receipt.getId(), bucketName, s3Key);

        return ResponseEntity.ok(Map.of(
                "receiptId", receipt.getId(),
                "status", "PROCESSING_INITIATED"
        ));
    }

    /**
     * 2. Local File Sandbox Processing (Development Testing Interface)
     */
    @Transactional
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadLocal(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("sub");
        log.info("User session context {} initialized local multipart parse upload", username);

        try {
            byte[] cleanImageBytes = file.getInputStream().readAllBytes();
            ExtractionResult result = billAnalyzer.analyze(cleanImageBytes);

            Receipt receipt = new Receipt();
            receipt.setUserId(username);
            receipt.setTotalAmount(result.getTotalAmount());
            receipt.setMerchantName(result.getMerchantName());
            receipt.setPurchaseDate(result.getPurchaseDate());
            receipt.setStatus(ReceiptStatus.UPLOADED);
            receipt.setS3Key("local/" + UUID.randomUUID());

            if (result.getLineItems() != null) {
                List<ReceiptItem> entityItems = result.getLineItems().stream().map(dto -> {
                    ReceiptItem item = new ReceiptItem();
                    item.setDescription(dto.getDescription());
                    item.setQuantity(dto.getQuantity());
                    item.setTotalPrice(dto.getPrice());
                    item.setUnitPrice(dto.getUnitPrice());
                    item.setReceipt(receipt);
                    return item;
                }).collect(Collectors.toList());
                receipt.setItems(entityItems);
            }

            receiptRepository.save(receipt);
            receiptProcessor.processReward(receipt);

            return ResponseEntity.ok(Map.of(
                    "id", receipt.getId(),
                    "status", receipt.getStatus(),
                    "merchant", result.getMerchantName()
            ));
        } catch (Exception e) {
            log.error("Unhandled exception inside local file upload transaction boundary:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "OCR parsing extraction sub-system error"));
        }
    }

    /**
     * 3. Points Status Aggregator Endpoint (Connects straight to your updated RewardStore dashboard)
     */
    @GetMapping("/payout-status")
    public ResponseEntity<PayoutStatusResponse> getPayoutStatus(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("sub");
        if (username == null) {
            username = jwt.getClaimAsString("username");
        }

        // Handoff directly to your revised RewardService business layer instance
        PayoutStatusResponse response = rewardService.getUserPointsDashboard(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/version")
    public String version() {
        return "v4-points-loyalty-engine-ready";
    }
}