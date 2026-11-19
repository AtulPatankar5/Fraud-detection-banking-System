package com.banking.notificationservice.repository;

import com.banking.notificationservice.entity.ProcessedNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProcessedNotificationRepository extends JpaRepository<ProcessedNotification, Long> {

    // Atomically claims a notification key.
    // Returns 1 for a new key, 0 if it already exists.

    @Modifying
    @Transactional
    @Query(value = """
        INSERT IGNORE INTO processed_notifications
        (idempotency_key, event_id, notification_type,
         status, created_at, updated_at)
        VALUES (:key, :eventId, :type,
                'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, nativeQuery = true)
    int claimNotification(
            @Param("key") String key,
            @Param("eventId") String eventId,
            @Param("type") String type
    );


    @Modifying
    @Transactional
    @Query(value = """
        UPDATE processed_notifications
        SET status = 'SENT',
            updated_at = CURRENT_TIMESTAMP
        WHERE idempotency_key = :key
        """, nativeQuery = true)
    int markAsSent(@Param("key") String key);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM processed_notifications
            WHERE idempotency_key = :key
            """, nativeQuery = true)
    int deleteClaim(@Param("key") String key);
}
