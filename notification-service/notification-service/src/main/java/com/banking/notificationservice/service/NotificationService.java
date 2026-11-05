
package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    // Safely converts String, Integer, Double, BigDecimal, etc. to String.
    private String getValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }

    // OTP generated notification
    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String, Object> payload) {
        try {
            String accountNumber = getValue(payload, "accountNumber");
            String otp = getValue(payload, "otp");
            String amount = getValue(payload, "amount");
            String transactionId = getValue(payload, "transactionId");
            String reason = getValue(payload, "reason");

            String message = String.format(
                    "Suspicious activity detected on your account. " +
                            "Reason: %s. " +
                            "Transaction of %s is pending. " +
                            "Your OTP is %s and is valid for 5 minutes. " +
                            "Transaction ID: %s. " +
                            "If this wasn't you, please contact support.",
                    reason, amount, otp, transactionId
            );

            sendAlert(accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED", message);

        } catch (Exception e) {
            log.error("Error sending OTP notification", e);
        }
    }

    // Transaction completed notification
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload) {
        try {
            String senderAccountNumber = getValue(payload, "senderAccountNumber") != null ? getValue(payload, "senderAccountNumber") : getValue(payload, "accountNumber");

            String receiverAccountNumber =
                    getValue(payload, "receiverAccountNumber");

            String amount = getValue(payload, "amount");

            // Debit alert
            sendAlert(
                    senderAccountNumber,
                    "DEBIT ALERT",
                    String.format("%s debited from account %s",
                            amount, senderAccountNumber)
            );

            // Credit alert
            sendAlert(
                    receiverAccountNumber,
                    "CREDIT ALERT",
                    String.format("%s credited to account %s",
                            amount, receiverAccountNumber)
            );

        } catch (Exception e) {
            log.error("Error sending transaction notification", e);
        }
    }

    // Fraud detected notification
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String, Object> payload) {
        try {
            String senderAccountNumber = getValue(payload, "senderAccountNumber") != null ? getValue(payload, "senderAccountNumber") : getValue(payload, "accountNumber");

            String reason = getValue(payload, "reason");

            sendAlert(
                    senderAccountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                    String.format("%s has been blocked. Reason: %s",
                            senderAccountNumber, reason)
            );

        } catch (Exception e) {
            log.error("Error sending fraud alert notification", e);
        }
    }

    // Transaction refunded notification
    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(
            @Payload Map<String, Object> payload) {
        try {
            String senderAccountNumber = getValue(payload, "senderAccountNumber") != null ? getValue(payload, "senderAccountNumber") : getValue(payload, "accountNumber");

            String amount = getValue(payload, "amount");
            String reason = getValue(payload, "reason");

            String message = String.format(
                    "Your transaction of %s was cancelled. " +
                            "Reason: %s. " +
                            "%s has been refunded to account %s.",
                    amount, reason, amount, senderAccountNumber
            );

            sendAlert(
                    senderAccountNumber,
                    "REFUND PROCESSED",
                    message
            );

        } catch (Exception e) {
            log.error("Error sending refund notification", e);
        }
    }

    // Payment completed notification
    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(
            @Payload Map<String, Object> payload) {
        try {
            String accountNumber = getValue(payload, "accountNumber");
            String amount = getValue(payload, "amount");
            String razorpayPaymentId =
                    getValue(payload, "razorpayPaymentId");

            sendAlert(
                    accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format(
                            "Payment of %s completed successfully. Razorpay ID: %s",
                            amount, razorpayPaymentId
                    )
            );

        } catch (Exception e) {
            log.error("Error sending payment completion notification", e);
        }
    }

    // Payment failed notification
    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(
            @Payload Map<String, Object> payload) {
        try {
            String accountNumber = getValue(payload, "accountNumber");
            String amount = getValue(payload, "amount");

            sendAlert(
                    accountNumber,
                    "PAYMENT FAILED",
                    String.format(
                            "Your payment of %s could not be processed. " +
                                    "Please try again or contact support.",
                            amount
                    )
            );

        } catch (Exception e) {
            log.error("Error sending payment failure notification", e);
        }
    }

    // Common notification method
    private void sendAlert(
            String accountNumber,
            String subject,
            String message) {

        log.info("--------------------------------");
        log.info("Account Number: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("--------------------------------");
    }
}
