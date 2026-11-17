package com.banking.paymentservice.exception;

public enum ErrorCode {

    // Payment business errors
    PAYMENT_NOT_FOUND,
    PAYMENT_ALREADY_EXISTS,
    PAYMENT_ALREADY_PROCESSED,
    PAYMENT_FAILED,
    INVALID_PAYMENT_STATUS,
    PAYMENT_PROVIDER_ERROR,

    // Common business errors
    UNAUTHORIZED,
    ACCESS_DENIED,
    DATA_CONFLICT,

    // Validation errors
    VALIDATION_FAILED,
    INVALID_REQUEST,

    // System errors
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR,

    //WebHook Error
    WEBHOOK_SIGNATURE_INVALID,
    WEBHOOK_EVENT_INVALID
}
