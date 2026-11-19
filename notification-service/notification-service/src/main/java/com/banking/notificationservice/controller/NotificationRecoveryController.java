package com.banking.notificationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Step	Action
 * 1	Receives the failed event payload
 * 2	Checks required fields
 * 3	Publishes the event to payment.failed
 * 4	Notification Service consumes it again
 * 5	Idempotency service checks whether it was already processed
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationRecoveryController {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @PostMapping("/reprocess/payment-failed")
    public ResponseEntity<String> reprocessPaymentFailed(
            @RequestBody Map<String, Object> payload) {

        // Validate the event before republishing.
        if (payload.get("eventId") == null ||
                payload.get("accountNumber") == null ||
                payload.get("amount") == null ||
                payload.get("paymentId") == null) {

            return ResponseEntity.badRequest()
                    .body("Required event fields are missing");
        }

        String eventId = payload.get("eventId").toString();

        // Publish the original event again.
        kafkaTemplate.send(
                "payment.failed",
                eventId,
                    payload
        );

        return ResponseEntity.accepted()
                .body("Payment failure event queued for reprocessing: " + eventId);
    }
}