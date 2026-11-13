package com.banking.transactionservice.exception;

public enum ErrorCode {

    // Transaction errors
    TRANSACTION_NOT_FOUND,
    TRANSACTION_ALREADY_EXISTS,
    INVALID_TRANSACTION,
    INVALID_TRANSACTION_AMOUNT,
    TRANSACTION_FAILED,
    TRANSACTION_LIMIT_EXCEEDED,

    // Account-related errors
    ACCOUNT_NOT_FOUND,
    ACCOUNT_BLOCKED,
    INSUFFICIENT_BALANCE,

    // Saga errors
    SAGA_EXECUTION_FAILED,
    SAGA_COMPENSATION_FAILED,
    SAGA_TIMEOUT,

    // Validation errors
    INVALID_REQUEST,
    VALIDATION_FAILED,
    MISSING_REQUIRED_FIELD,

    // Security errors
    UNAUTHORIZED,
    ACCESS_DENIED,

    // System errors
    DATA_CONFLICT,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}