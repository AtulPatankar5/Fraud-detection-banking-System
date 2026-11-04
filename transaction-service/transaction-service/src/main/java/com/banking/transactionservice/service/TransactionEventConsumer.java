package com.banking.transactionservice.service;

import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;

    private final RedisTemplate redisTemplate;

    private static final long OTP_EXPIRY_MINUTES = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_OTP_GENERATE_TOPIC = "transaction.otp.generated";

    private final TransactionService transactionService;

    /**
     * consumed verification.required
     * Generate OTP and ask user for verification
     *
     * @param payload
     */

    @KafkaListener(topics = "verification.required")
    public void consumerVerificationRequired(@Payload Map<String, Object> payload) {
        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required  - Transaction {} and reason {}", transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException("Transaction not found : " + transactionId));


            //Idempotency- ensure that transaction is not sent to receiver or sender twice.
            // Also send OTP only when transaction is at processing state
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.warn("Transaction  {} not processing. Skipping ", transactionId);
                return;
            }

            // Generate 6 digit OTP
//            Example: `Math.random()` generates `0.45678` → Multiply by `900000` → `411102` → Add `100000` → `511102` (6-digit OTP).
            String otp = String.format("%06d", (int) (Math.random() * 900000 + 100000));

            //Store OTP in Redis - expires in 5 minutes
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);


            //Update Status  from processing to pending verification
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generate for transaction {}, expires in {} min",
                    transactionId, OTP_EXPIRY_MINUTES);

            // notify user -- Publish Event
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));

            // Kafka Event send to Notification service for OTP
            kafkaTemplate.send(TRANSACTION_OTP_GENERATE_TOPIC, transactionId, otpEvent);


        } catch (Exception e) {
            log.error("Error handling verification required :{}  ", e.getMessage());
        }

    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckCleanResult(@Payload Map<String, Object> payload) {

        try {
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);

        } catch (Exception e) {
            log.error("Error Processing fraud check result: {}", e.getMessage());
        }

    }
}
