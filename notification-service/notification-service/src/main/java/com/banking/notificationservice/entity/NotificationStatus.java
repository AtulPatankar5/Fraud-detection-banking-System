package com.banking.notificationservice.entity;

/**
 * PROCESSING- 	Notification processing has started
 * SENT	- Notification was successfully processed
 * FAILED -	Notification processing failed
 */
public enum NotificationStatus {
    PROCESSING,
    SENT,
    FAILED
}
