package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    // received from Transaction Service with OTP
    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String, Object> payload) {
        try {

            String accountNumber = (String) payload.get("accountNumber");
            String otp = (String) payload.get("otp");
            String amount = (String) payload.get("amount");
            String transactionId = (String) payload.get("transactionId");
            String reason = (String) payload.get("reason");

            // sent to user to Enter and verify OTP
            sendAlert(accountNumber, "TRANSACTION_VERIFICATION_REQUIRED", String.format("Suspicious Activity Detected on your account." + "Reason is %s ." + "Transaction of %s is pending." + "Your OTP is %s and valid for 5 minutes." + "If this wasn't you then ignore this message"));


        } catch (Exception e) {
            log.error("error sending OTP notification , {}", e.getMessage());
        }

    }

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        try {
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
            String amount = (String) payload.get("amount");
            //debit alert
            sendAlert(senderAccountNumber, "DEBIT ALERT", String.format("%s debited from account %s", amount, senderAccountNumber));

            //credit Alert
            sendAlert(senderAccountNumber, "CREDIT ALERT", String.format("%s credited in account %s", amount, receiverAccountNumber));
        } catch (Exception e) {
            log.error("ERROR sending OTP notification , {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumefraudDetected(@Payload Map<String, Object> payload) {

        try {
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            String reason = (String) payload.get("reason");

            sendAlert(senderAccountNumber, "SUSPICIOS ACTIVITY DETECTED", String.format("%s has been blocked. " + "Reason: %s", senderAccountNumber, reason));

        } catch (Exception e) {
            log.error("ERROR sending fraud alert notification , {}", e.getMessage());
        }
    }


    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(@Payload Map<String, Object> payload) {

        try {
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            String amount = (String) payload.get("amount");
            String reason = (String) payload.get("reason");

            sendAlert(senderAccountNumber, "REFUND PROCESSED", String.format("Your transaction of %s was cancelled." + "Reason: %s" + "%s has been refunded to account %s", amount, reason, amount, senderAccountNumber));
        } catch (Exception e) {
            log.error("ERROR sending refund notification , {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {

        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount");
            String razorpayPaymentId = (String) payload.get("razorpayPaymentId");

            sendAlert(accountNumber, "PAYMENT SUCCESSFUL", String.format("Payment of %s completed." + "RazorPay Id: %s  ", amount, razorpayPaymentId));
        } catch (Exception e) {
            log.error("ERROR sending payment complete notification , {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(@Payload Map<String, Object> payload) {

        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount");

            sendAlert(accountNumber, "PAYMENT FAILED", String.format("Your payment of  %s could not be processed." + "Please try again or contact admin", amount));
        } catch (Exception e) {
            log.error("ERROR sending payment failure notification , {}", e.getMessage());
        }
    }

    private void sendAlert(String accountNumber, String subject, String message) {

        log.info("--------------------------------");
        log.info("Account Number: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("--------------------------------");
    }
}
