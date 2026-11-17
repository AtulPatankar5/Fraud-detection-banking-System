package com.banking.paymentservice.service;

import com.banking.paymentservice.entity.OutboxEvent;
import com.banking.paymentservice.entity.OutboxStatus;
import com.banking.paymentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class acts like a delivery person.
 * <p>
 * It continuously checks the outbox table for pending tasks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    //    "Run this method again 5 seconds after the previous execution finishes."
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        PageRequest.of(0, 20)
                );

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {

        try {
            //Send this message to Kafka and wait up to 10 seconds for confirmation.
            // Send the event to Kafka and wait for broker acknowledgement.
            kafkaTemplate.send(
                    event.getTopic(),
                    event.getMessageKey(),
                    event.getPayload()
            ).get(10, TimeUnit.SECONDS);

            // Mark as published only after Kafka confirms success.
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(java.time.Instant.now());
            event.setLastError(null);

            outboxEventRepository.save(event);

            log.info(
                    "Outbox event published successfully. Event ID: {}, Topic: {}",
                    event.getEventId(),
                    event.getTopic()
            );

        } catch (Exception exception) {

            event.setAttempts(event.getAttempts() + 1);
            event.setLastError(
                    exception.getMessage() == null
                            ? "Unknown Kafka publishing error"
                            : exception.getMessage()
            );

            // Keep it PENDING so the next scheduled run retries it.
            outboxEventRepository.save(event);

            log.error(
                    "Failed to publish outbox event: {}. Attempt: {}",
                    event.getEventId(),
                    event.getAttempts(),
                    exception
            );
        }
    }
}
