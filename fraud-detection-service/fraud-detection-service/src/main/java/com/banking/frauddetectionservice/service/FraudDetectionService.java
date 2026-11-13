package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.exception.*;
import com.banking.frauddetectionservice.exception.customExceptions.AccountServiceException;
import com.banking.frauddetectionservice.exception.customExceptions.FraudDetectionException;
import com.banking.frauddetectionservice.exception.customExceptions.FraudInfrastructureException;
import com.banking.frauddetectionservice.exception.customExceptions.InvalidFraudCheckRequestException;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT = "fraud.check.clean";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${fraud.max-transaction-per-minute}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;


    private String getRequiredValue(Map<String, Object> payload, String key) {

        Object value = payload.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new InvalidFraudCheckRequestException(
                    "Missing required field: " + key
            );
        }

        return value.toString();
    }

    public void checkTransaction(Map<String, Object> payload) {

        // 1. Validate incoming Kafka payload
        if (payload == null || payload.isEmpty()) {
            throw new InvalidFraudCheckRequestException("Fraud check payload cannot be empty");
        }

        String transactionId = getRequiredValue(payload, "transactionId");
        String senderAccountNumber = getRequiredValue(payload, "senderAccountNumber");

        BigDecimal amount;

        try {
            Object amountValue = payload.get("amount");

            if (amountValue == null) {
                throw new InvalidFraudCheckRequestException("Transaction amount is required");
            }

            amount = new BigDecimal(amountValue.toString());

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidFraudCheckRequestException("Transaction amount must be greater than zero");
            }

        } catch (NumberFormatException ex) {
            throw new InvalidFraudCheckRequestException("Invalid transaction amount");
        }

        // 2. Fetch balance from Account Service
        BigDecimal balance;

        try {
            balance = accountServiceClient.getBalance(senderAccountNumber);

            if (balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
                throw new FraudDetectionException(
                        "Invalid account balance received from Account Service",
                        null
                );
            }

        } catch (FeignException ex) {
            log.error("Account Service call failed for account: {}", senderAccountNumber, ex);

            throw new AccountServiceException(
                    "Unable to fetch account balance",
                    ex
            );
        }

        log.info("Checking transaction: {}, account: {}, amount: {}, balance: {}",
                transactionId, senderAccountNumber, amount, balance);

        // 3. Perform fraud checks
        FraudCheckResult result;

        try {
            result = performFraudChecks(senderAccountNumber, amount, balance);

        } catch (DataAccessException ex) {
            log.error("Redis operation failed during fraud check for transaction: {}",
                    transactionId, ex);

            throw new FraudInfrastructureException(
                    ErrorCode.REDIS_OPERATION_FAILED,
                    "Unable to perform fraud checks because Redis is unavailable",
                    ex
            );
        } catch (Exception ex) {
            log.error("Unexpected fraud detection failure for transaction: {}",
                    transactionId, ex);

            throw new FraudDetectionException(
                    "Fraud detection processing failed",
                    ex
            );
        }

        // 4. Publish the appropriate event
        if (result.isFraud()) {

            log.info("Suspicious activity detected for account: {}, reason: {}",
                    senderAccountNumber, result.getReason());

            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("senderAccountNumber", senderAccountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", result.getReason());

            publishEvent(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationEvent);

        } else {

            log.info("Transaction {} passed fraud checks", transactionId);

            Map<String, Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);

            publishEvent(FRAUD_CHECK_CLEAN_RESULT, transactionId, transactionCleanEvent);
        }
    }


    private void publishEvent(String topic, String key, Object event) {

        try {
            kafkaTemplate.send(topic, key, event)
                    .get(10, TimeUnit.SECONDS);

            log.info("Successfully published event to topic: {}, key: {}", topic, key);

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new FraudInfrastructureException(
                    ErrorCode.KAFKA_PUBLISH_FAILED,
                    "Kafka event publishing was interrupted",
                    ex
            );

        } catch (ExecutionException | TimeoutException ex) {

            log.error("Failed to publish event to topic: {}, key: {}", topic, key, ex);

            throw new FraudInfrastructureException(
                    ErrorCode.KAFKA_PUBLISH_FAILED,
                    "Unable to publish event to Kafka topic: " + topic,
                    ex
            );

        } catch (Exception ex) {

            log.error("Unexpected Kafka publishing error for topic: {}", topic, ex);

            throw new FraudInfrastructureException(
                    ErrorCode.KAFKA_PUBLISH_FAILED,
                    "Unexpected error while publishing Kafka event",
                    ex
            );
        }
    }
    private FraudCheckResult performFraudChecks(String accountNumber,
                                                BigDecimal amount,
                                                BigDecimal balance) {

        //Pattern 1: Velocity Check - no. of transactions
        if (isVelocityExceeded(accountNumber)) {
            return new FraudCheckResult(true, "Too many Transactions in 60 seconds" +
                    "-  Velocity Limit exceeded");
        }

        // Pattern 2: Amount check
        if (isAmountSuspicious(accountNumber, amount)) {
            return new FraudCheckResult(true, "Unusual Transaction Amount" +
                    "-  Exceed 3X your average");
        }

        //Pattern 3: Balance Check and exceeds 90% of account balance
        if (balance.compareTo(BigDecimal.ZERO) > 0 &&
                isBalanceCheckFailed(balance, amount)) {
            return new FraudCheckResult(true, "Transaction Exceeds 90% of account balance");
        }
        return new FraudCheckResult(false, null);

    }

    private boolean isBalanceCheckFailed(BigDecimal balance, BigDecimal amount) {

        BigDecimal maxAllowed = balance.multiply(BigDecimal.valueOf(maxBalancePercentage));
        log.info("Balance is {}, allowed balance is {} and suspicious : {}",
                balance, maxAllowed, amount.compareTo(maxAllowed) > 0);

        return amount.compareTo(maxAllowed) > 0;
    }

    //    It uses Redis to remember the average transaction amount for each account.
    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {

        // Step 1: Create a unique Redis key for this account.
        // Example: fraud.avg_amountACC123
        String avgKey = "fraud.avg_amount:" + accountNumber;

        // Step 2: Get the previously stored average transaction amount from Redis.
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        // Step 3: Check whether this account has any previous transaction history.
        if (avgStr == null) {

            // If no previous average exists, store the current transaction amount.
            redisTemplate.opsForValue().set(avgKey, amount.toString());

            // First transaction cannot be compared with previous transactions.
            return false;
        }


        // Step 4: Convert the stored average from String into BigDecimal.
        BigDecimal avgAmount = new BigDecimal(avgStr);

        // Step 5: Calculate the suspicious amount limit.
        // Example: Average = 1000 and multiplier = 3.
        // Threshold = 1000 * 3 = 3000.
        BigDecimal threshold = avgAmount.multiply(
                BigDecimal.valueOf(suspiciousAmountMultiplier)
        );


        // Step 6: Calculate the updated average using the old average
        // and the current transaction amount.
        BigDecimal newAvg = avgAmount.add(amount)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);


        // Step 7: Save the updated average in Redis.
        redisTemplate.opsForValue().set(avgKey, newAvg.toString());


        // Step 8: Print transaction amount, threshold and suspicious status.
        log.info("Amount check - amount: {} threshold: {} suspicious: {}",
                amount, threshold, amount.compareTo(threshold) > 0);


        // Step 9: Check whether the current transaction exceeds the threshold.
        // true = Suspicious transaction.
        // false = Normal transaction.
        return amount.compareTo(threshold) > 0;
    }

    // Checks whether an account is making too many transactions within 60 seconds.
    private boolean isVelocityExceeded(String accountNumber) {

        // Step 1: Create a unique Redis key for this bank account. Example: fraud:velocity:ACC123
        String key = "fraud:velocity:" + accountNumber;


        // Step 2: Increase the transaction counter in Redis by 1.
        Long count = redisTemplate.opsForValue().increment(key);


        // Step 3: Check whether this is the first transaction.
        if (count != null && count == 1) {
            // Start a 60-second expiry timer for this Redis key. After 60 seconds, Redis automatically deletes the key.
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }


        // Step 4: Print account number, current transaction count and maximum allowed transaction count in the logs.
        log.info("Velocity check account: {} count {}/{}",
                accountNumber, count, maxTransactionsPerMinute);


        // Step 5: Check whether the transaction count exceeds the limit.
        // Example: If maximum limit = 5 and count = 6, return true. Otherwise, return false.
        return count != null && count > maxTransactionsPerMinute;
    }

}
