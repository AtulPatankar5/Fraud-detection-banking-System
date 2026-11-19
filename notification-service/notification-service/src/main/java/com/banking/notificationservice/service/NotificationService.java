
package com.banking.notificationservice.service;

import com.banking.notificationservice.client.AccountServiceClient;
import com.banking.notificationservice.dto.AccountResponse;
import com.banking.notificationservice.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationIdempotencyService idempotencyService;

    private final EmailService emailService;
    private final AccountServiceClient accountServiceClient;

    // Safely converts different data types to String.
    private String getValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private String requireValue(Map<String, Object> payload, String key) {
        String value = getValue(payload, key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required event field is missing: " + key);
        }

        return value;
    }

    // 1. OTP generated notification
    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String, Object> payload) {

        String accountNumber = requireValue(payload, "accountNumber");
        String amount = requireValue(payload, "amount");
        String otp = requireValue(payload, "otp");
        String transactionId = requireValue(payload, "transactionId");

        String eventId = requireValue(payload, "eventId");
        String reason = getValue(payload, "reason");

        String message = String.format(
                "Suspicious activity detected on your account. " +
                        "Reason: %s. " +
                        "Transaction of %s is pending. " +
                        "Your OTP is %s and is valid for 5 minutes. " +
                        "Transaction ID: %s. " +
                        "If this wasn't you, please contact support.",
                reason, amount, otp, transactionId);

        idempotencyService.execute(
                eventId,
                "TRANSACTION_OTP",
                accountNumber,
                () -> sendAlert(
                        accountNumber,
                        "TRANSACTION VERIFICATION REQUIRED",
                        message));
    }

    // 2. Transaction completed notification
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload) {

        String senderAccount = getValue(payload, "senderAccountNumber");

        if (senderAccount == null || senderAccount.isBlank()) {
            senderAccount = getValue(payload, "accountNumber");
        }

        if (senderAccount == null || senderAccount.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender account number is missing");
        }

        final String senderAccountNumber = senderAccount;

        String receiverAccountNumber = requireValue(payload, "receiverAccountNumber");

        String amount = requireValue(payload, "amount");
        String eventId = requireValue(payload, "eventId");

        // Debit notification
        idempotencyService.execute(
                eventId,
                "TRANSACTION_DEBIT",
                senderAccountNumber,
                () -> sendAlert(
                        senderAccountNumber,
                        "DEBIT ALERT",
                        String.format(
                                "%s debited from account %s",
                                amount,
                                senderAccountNumber)));

        // Credit notification
        idempotencyService.execute(
                eventId,
                "TRANSACTION_CREDIT",
                receiverAccountNumber,
                () -> sendAlert(
                        receiverAccountNumber,
                        "CREDIT ALERT",
                        String.format(
                                "%s credited to account %s",
                                amount,
                                receiverAccountNumber)));
    }

    // 3. Fraud detected notification
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String, Object> payload) {

        String accountNumber = getValue(payload, "senderAccountNumber");

        if (accountNumber == null || accountNumber.isBlank()) {
            accountNumber = getValue(payload, "accountNumber");
        }

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender account number is missing");
        }

        final String recipientAccount = accountNumber;

        String eventId = requireValue(payload, "eventId");
        String reason = getValue(payload, "reason");

        idempotencyService.execute(
                eventId,
                "FRAUD_ALERT",
                recipientAccount,
                () -> sendAlert(
                        recipientAccount,
                        "SUSPICIOUS ACTIVITY DETECTED",
                        String.format(
                                "%s has been blocked. Reason: %s",
                                recipientAccount,
                                reason)));
    }

    // 4. Transaction refunded notification
    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(
            @Payload Map<String, Object> payload) {

        String accountNumber = getValue(payload, "senderAccountNumber");

        if (accountNumber == null || accountNumber.isBlank()) {
            accountNumber = getValue(payload, "accountNumber");
        }

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender account number is missing");
        }

        final String recipientAccount = accountNumber;

        String amount = requireValue(payload, "refundAmount");
        String eventId = requireValue(payload, "eventId");
        String reason = getValue(payload, "reason");

        String message = String.format(
                "Your transaction of %s was cancelled. " +
                        "Reason: %s. " +
                        "%s has been refunded to account %s.",
                amount, reason, amount, recipientAccount);

        idempotencyService.execute(
                eventId,
                "TRANSACTION_REFUND",
                recipientAccount,
                () -> sendAlert(
                        recipientAccount,
                        "REFUND PROCESSED",
                        message));
    }

    // 5. Payment completed notification
    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
            @Payload Map<String, Object> payload) {

        String accountNumber = requireValue(payload, "accountNumber");
        String amount = requireValue(payload, "amount");
        String eventId = requireValue(payload, "eventId");

        String razorpayPaymentId = getValue(payload, "razorpayPaymentId");

        idempotencyService.execute(
                eventId,
                "PAYMENT_SUCCESS",
                accountNumber,
                () -> sendAlert(
                        accountNumber,
                        "PAYMENT SUCCESSFUL",
                        String.format(
                                "Payment of %s completed successfully. Razorpay ID: %s",
                                amount,
                                razorpayPaymentId)));
    }

    // 6. Payment failed notification
    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(
            @Payload Map<String, Object> payload) {

        String accountNumber = requireValue(payload, "accountNumber");
        String amount = requireValue(payload, "amount");
        String eventId = requireValue(payload, "eventId");

        idempotencyService.execute(
                eventId,
                "PAYMENT_FAILED",
                accountNumber,
                () -> sendAlert(
                        accountNumber,
                        "PAYMENT FAILED",
                        String.format(
                                "Your payment of %s could not be processed. " +
                                        "Please try again or contact support.",
                                amount)));
    }

    // Common notification method

    private void sendAlert(
            String accountNumber,
            String subject,
            String message) {

        log.info("Starting notification process for account: {}",
                maskAccountNumber(accountNumber));

        try {
            // 1. Fetch account details
            ApiResponse<AccountResponse> response =
                    accountServiceClient.getAccountDetails(accountNumber);

            // 2. Validate Account Service response
            if (response == null ||
                    !response.isSuccess() ||
                    response.getData() == null) {

                throw new RuntimeException(
                        "Unable to retrieve account details");
            }

            // 3. Extract registered email
            AccountResponse account = response.getData();
            String email = account.getEmail();

            if (email == null || email.isBlank()) {
                throw new RuntimeException(
                        "Registered email is missing for account: "
                                + maskAccountNumber(accountNumber));
            }

            // 4. Send email
            emailService.sendEmail(email, subject, message);

            log.info("Email notification sent successfully for account: {}",
                    maskAccountNumber(accountNumber));

        } catch (Exception ex) {

            log.error("Notification failed for account {}: {}",
                    maskAccountNumber(accountNumber),
                    ex.getMessage());

            // Propagate exception for Kafka retry/DLT handling
            throw new RuntimeException(
                    "Failed to process account notification", ex);
        }
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "UNKNOWN";
        }

        if (accountNumber.length() <= 4) {
            return "****";
        }

        return "****" +
                accountNumber.substring(accountNumber.length() - 4);
    }
}
