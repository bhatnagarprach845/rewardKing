package com.bhatn.rewardking.controller;

import com.bhatn.rewardking.dto.ExtractionResult;
import com.bhatn.rewardking.entity.Receipt;
import com.bhatn.rewardking.entity.ReceiptItem;
import com.bhatn.rewardking.entity.ReceiptStatus;
import com.bhatn.rewardking.repository.ReceiptRepository;
import com.bhatn.rewardking.service.ReceiptProcessor;
import com.bhatn.rewardking.service.ocr.BillAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class ReceiptController {

    private final ReceiptProcessor receiptProcessor;
    private final ReceiptRepository receiptRepository;
    private final BillAnalyzer billAnalyzer;
    private final S3Client s3Client;
    private final String bucketName;

    public ReceiptController(
            ReceiptProcessor receiptProcessor,
            ReceiptRepository receiptRepository,
            BillAnalyzer billAnalyzer,
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.receiptProcessor = receiptProcessor;
        this.receiptRepository = receiptRepository;
        this.billAnalyzer = billAnalyzer;
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * 0. Production upload pathway: the client posts the image directly to the
     * Lambda (multipart), which stores it in S3 itself (no presigned URL - the
     * Lambda already holds valid credentials) and kicks off the same async
     * OCR/fraud-detection/reward pipeline used by the S3 flow.
     */
    @PostMapping("/receipts/upload")
    public ResponseEntity<Map<String, Object>> uploadReceipt(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("sub");
        String s3Key = "receipts/" + username + "/" + UUID.randomUUID();

        try {
            byte[] imageBytes = file.getInputStream().readAllBytes();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(imageBytes)
            );

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
        } catch (Exception e) {
            log.error("Failed to upload receipt to S3 for user {}:", username, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to store receipt image"));
        }
    }

    /**
     * 1. Production S3 Processing Pathway (used when the client already has an
     * s3Key, e.g. from a direct S3 upload elsewhere).
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
     * 3. Lets the client poll a receipt's OCR/reward processing status after
     * kicking off /receipts/upload or /process-s3.
     */
    @GetMapping("/receipts/{id}/status")
    public ResponseEntity<Map<String, Object>> getReceiptStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("sub");

        return receiptRepository.findById(id)
                .filter(receipt -> receipt.getUserId().equals(username))
                .map(receipt -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", receipt.getId(),
                        "status", receipt.getStatus().name(),
                        "merchant", receipt.getMerchantName() != null ? receipt.getMerchantName() : ""
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.<String, Object>of("error", "Receipt not found")));
    }

    @GetMapping("/version")
    public String version() {
        return "v4-points-loyalty-engine-ready";
    }
}
