package com.banking.paymentservice.entity;

/**
 *
 * PENDING: Message still needs to be sent to Kafka.
 * PUBLISHED: Kafka has successfully acknowledged the message.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}