
package com.banking.paymentservice.service;

import com.banking.paymentservice.entity.WebhookEvent;
import com.banking.paymentservice.entity.WebhookEventStatus;
import com.banking.paymentservice.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 1. Run automatically every 5 seconds.
 * 2. Find webhook records with status RECEIVED.
 * 3. Call paymentService.processWebhookEvent(id).
 * 4. If processing fails, record the error and retry later.
 * 5. Stop automatic retries after 5 failed attempts, marking the event FAILED for investigation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookEventProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final WebhookEventRetryService retryService;

    // Check for new webhooks every 5 seconds.
    @Scheduled(fixedDelay = 5000)
    public void processReceivedWebhooks() {

        // Fetch webhooks that Razorpay events have saved,
        // but our application has not processed yet.
        List<WebhookEvent> events =
                webhookEventRepository
                        .findTop20ByStatusOrderByReceivedAtAsc(
                                WebhookEventStatus.RECEIVED
                        );

        for (WebhookEvent event : events) {

            try {
                log.info("Processing webhook event: {}", event.getEventId());

                // Call the actual payment processing method.
                paymentService.processWebhookEvent(event.getId());

                log.info("Webhook processing completed: {}", event.getEventId());

            } catch (Exception exception) {

                log.error(
                        "Webhook processing failed: {}",
                        event.getEventId(),
                        exception
                );

                // Record failure separately so the failed transaction
                // does not roll back the attempt counter.
                retryService.recordFailure(event.getId(), exception);
            }
        }
    }
}
