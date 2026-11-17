
package com.banking.paymentservice.repository;

import com.banking.paymentservice.entity.WebhookEvent;
import com.banking.paymentservice.entity.WebhookEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEvent, Long> {

    boolean existsByEventId(String eventId);

    List<WebhookEvent> findTop50ByStatusOrderByReceivedAtAsc(
            WebhookEventStatus status
    );


    List<WebhookEvent> findTop20ByStatusOrderByReceivedAtAsc(
            WebhookEventStatus status
    );

}