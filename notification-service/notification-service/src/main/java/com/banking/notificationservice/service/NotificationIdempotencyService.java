
package com.banking.notificationservice.service;

import com.banking.notificationservice.repository.ProcessedNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The service follows this sequence:
 * 1. Generate a deterministic key from event ID, notification type and recipient.
 * 2. Hash the key.
 * 3. Try to claim it in the database.
 * 4. If it already exists, skip the duplicate.
 * 5. If it is new, execute the notification.
 * 6. Mark it SENT.
 * 7. If sending fails, delete the claim and rethrow the exception so your Kafka retry/error handler can process it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationIdempotencyService {

    private final ProcessedNotificationRepository repository;

    public void execute(
            String eventId,
            String notificationType,
            String recipientAccount,
            Runnable notificationAction) {

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Event ID is missing");
        }

        if (recipientAccount == null || recipientAccount.isBlank()) {
            throw new IllegalArgumentException("Recipient account is missing");
        }

        if (notificationType == null || notificationType.isBlank()) {
            throw new IllegalArgumentException(
                    "Notification type is missing"
            );
        }
        String rawKey = eventId + "|"
                + notificationType + "|"
                + recipientAccount;

        String idempotencyKey = generateSha256(rawKey);

        int claimed = repository.claimNotification(
                idempotencyKey,
                eventId,
                notificationType
        );

        if (claimed == 0) {
            log.info(
                    "Duplicate notification skipped. Event ID: {}, Type: {}",
                    eventId,
                    notificationType
            );
            return;
        }

        try {
            notificationAction.run();

            repository.markAsSent(idempotencyKey);

            log.info(
                    "Notification marked as SENT. Event ID: {}, Type: {}",
                    eventId,
                    notificationType
            );

        } catch (RuntimeException exception) {

            // Allow Kafka retry to try this notification again.
            repository.deleteClaim(idempotencyKey);

            throw exception;
        }
    }

    private String generateSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available",
                    exception
            );
        }
    }
}
