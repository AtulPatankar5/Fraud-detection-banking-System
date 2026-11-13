package com.banking.accountservice.exception;

public enum ErrorCode {

    // Account-related errors
    ACCOUNT_NOT_FOUND,
    ACCOUNT_ALREADY_EXISTS,
    ACCOUNT_BLOCKED,
    ACCOUNT_INACTIVE,

    // Transaction-related errors
    INSUFFICIENT_BALANCE,
    INVALID_TRANSACTION_AMOUNT,
    TRANSACTION_LIMIT_EXCEEDED,

    // Request validation errors
    INVALID_REQUEST,
    VALIDATION_FAILED,
    MISSING_REQUIRED_FIELD,

    // Authentication and authorization errors
    UNAUTHORIZED,
    ACCESS_DENIED,

    // Database and system errors
    DATA_CONFLICT,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
