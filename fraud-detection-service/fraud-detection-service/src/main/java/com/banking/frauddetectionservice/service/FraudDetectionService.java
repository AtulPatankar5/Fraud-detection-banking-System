package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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


    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String senderAccountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        //fetch real balance from account service
        BigDecimal balance = accountServiceClient.getBalance(senderAccountNumber);

        log.info("Checking Transaction : {} account :{}, amount: {}, balance:{}",
                transactionId, senderAccountNumber, amount, balance);
        FraudCheckResult result = performFraudChecks(senderAccountNumber, amount, balance);

        if (result.isFraud()) {
            log.info("Suspicious Activity Detected in account {}" +
                            " and reason: {} - requesting OTP verification",
                    senderAccountNumber, result.getReason());

            // Verification Required to be triggered to user to check whether it is you. Send OTP to user
            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("senderAccountNumber", senderAccountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", result.getReason());

            // send to kafka to publish and send to Transaction service for OTP generation adn storing in redis
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationEvent);


        } else {
            log.info("Transaction is clean");
            Map<String, Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);
            // send to transaction service for clean transaction
            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT, transactionId, transactionCleanEvent);
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
