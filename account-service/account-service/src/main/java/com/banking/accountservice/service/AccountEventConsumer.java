package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /**
     * Consume transaction.completed event from kafka
     * Amount Credits to receiver account
     * No Fraud detected
     *
     * @param payload
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload
    ) {
        try {

            String receiverAccount = payload.get("receiverAccountNumber").toString();
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Crediting account : {} and amount : {}", receiverAccount, amount);
            accountService.creditBalance(receiverAccount, amount);


        } catch (Exception e) {
            log.error("Error Crediting amount: {}", e.getMessage());
        }
    }

    /**
     * Consume fraud.detected event from kafka.
     * Blocks the flagged account.
     *
     * @param payload
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = payload.get("senderAccountNumber") != null
                    ? payload.get("senderAccountNumber").toString()
                    : payload.get("accountNumber").toString();
            log.info("Fraud Detected. Blocking Account : {}", accountNumber);
            accountService.blockAccount(accountNumber);

            log.info("Account blocked. Blocking Account : {}", accountNumber);
        } catch (Exception e) {
            log.error("Error Blocking Account: {}", e.getMessage());
        }
    }
}
