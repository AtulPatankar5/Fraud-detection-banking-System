
package com.banking.paymentservice.service;

import com.banking.paymentservice.entity.WebhookEvent;
import com.banking.paymentservice.entity.WebhookEventStatus;
import com.banking.paymentservice.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookEventRetryService {

    private static final int MAX_ATTEMPTS = 5;

    private final WebhookEventRepository webhookEventRepository;

    /**
     * REQUIRES_NEW means this failure-recording operation gets its own database transaction.
     * Even when processWebhookEvent() rolls back, the attempt count and error can still be saved.
     *
     * Record a failed processing attempt without losing the retry information when the main processing
     * transaction rolls back.
     * @param webhookEventId
     * @param exception
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long webhookEventId, Exception exception) {

        WebhookEvent event = webhookEventRepository.findById(webhookEventId)
                .orElse(null);

        if (event == null ||
                event.getStatus() == WebhookEventStatus.PROCESSED) {
            return;
        }

        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);

        String error = exception.getMessage() == null
                ? "Unknown webhook processing error"
                : exception.getMessage();

        // Prevent excessively long error messages.
        event.setLastError(
                error.length() > 1000 ? error.substring(0, 1000) : error
        );

        if (attempts >= MAX_ATTEMPTS) {

            event.setStatus(WebhookEventStatus.FAILED);

            log.error(
                    "Webhook event permanently failed after {} attempts: {}",
                    attempts,
                    event.getEventId()
            );

        } else {

            // Keep it eligible for the next scheduled retry.
            event.setStatus(WebhookEventStatus.RECEIVED);

            log.warn(
                    "Webhook event will be retried. Attempt: {}",
                    attempts
            );
        }

        webhookEventRepository.save(event);
    }
}
