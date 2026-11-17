
package com.banking.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * This is simply the Java representation of the outbox_events database table.
 * <p>
 * Think of each record as one task waiting to be sent to Kafka.
 */
@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_event_id",
                        columnNames = "event_id"
                )
        },
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stable event identifier. Used for idempotency.
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    // Kafka topic, e.g. payment.completed
    @Column(nullable = false)
    private String topic;

    // Kafka message key
    @Column(name = "message_key", nullable = false)
    private String messageKey;

    // JSON payload to publish
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
    }
}
