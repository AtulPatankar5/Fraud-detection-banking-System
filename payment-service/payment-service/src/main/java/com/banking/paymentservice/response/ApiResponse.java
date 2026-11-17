package com.banking.paymentservice.response;

import com.banking.paymentservice.exception.ErrorCode;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ErrorDetails error,
        Instant timestamp
) {

    // Success response
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                Instant.now()
        );
    }

    // Failure response
    public static <T> ApiResponse<T> failure(
            String message,
            ErrorCode errorCode,
            String details
    ) {
        return new ApiResponse<>(
                false,
                message,
                null,
                new ErrorDetails(errorCode.name(), details),
                Instant.now()
        );
    }

    // Standard error details
    public record ErrorDetails(
            String code,
            String details
    ) {
    }
}
