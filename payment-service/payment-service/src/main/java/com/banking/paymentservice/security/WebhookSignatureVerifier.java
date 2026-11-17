package com.banking.paymentservice.security;


import com.banking.paymentservice.exception.BusinessException;
import com.banking.paymentservice.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Accept the original HTTP request bytes.
 * Generate an HMAC-SHA256 hash.
 * Compare it with the signature sent by Razorpay.
 * Reject invalid signatures.
 */
@Component
@Slf4j
public class WebhookSignatureVerifier {

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    public void verifySignature(
            byte[] rawBody,
            String receivedSignature) {

        if (rawBody == null || receivedSignature == null
                || !receivedSignature.matches("[a-fA-F0-9]{64}")) {

            throw new BusinessException(
                    ErrorCode.WEBHOOK_SIGNATURE_INVALID,
                    "Invalid webhook signature"
            );
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            mac.init(secretKey);

            // Calculate expected signature using ORIGINAL request body
            byte[] expectedSignature = mac.doFinal(rawBody);

            // Convert Razorpay's hexadecimal signature into bytes
            byte[] actualSignature =
                    HexFormat.of().parseHex(receivedSignature);

            // Constant-time comparison
            boolean valid = MessageDigest.isEqual(
                    expectedSignature,
                    actualSignature
            );

            if (!valid) {
                log.warn("Razorpay webhook signature verification failed");

                throw new BusinessException(
                        ErrorCode.WEBHOOK_SIGNATURE_INVALID,
                        "Webhook signature verification failed"
                );
            }

            log.debug("Razorpay webhook signature verified successfully");

        } catch (BusinessException exception) {
            throw exception;

        } catch (Exception exception) {

            log.error("Error verifying Razorpay webhook signature", exception);

            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to verify webhook signature"
            );
        }
    }
}
