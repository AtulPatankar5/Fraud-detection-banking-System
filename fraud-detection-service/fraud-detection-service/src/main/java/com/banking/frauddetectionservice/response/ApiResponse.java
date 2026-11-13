package com.banking.frauddetectionservice.response;
import com.banking.frauddetectionservice.exception.ErrorCode;

import java.time.Instant;

/**
 * Common response for failure and Success
 * <p>
 * Component	Purpose
 * T data	Supports any response DTO or entity
 * success	Indicates whether the request succeeded
 * message	Human-readable response message
 * ErrorDetails	Contains machine-readable error code and details
 * Instant timestamp	UTC timestamp of the response
 *
 * @param success
 * @param message
 * @param data
 * @param error
 * @param timestamp
 * @param <T>
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        ErrorDetails error,
        Instant timestamp
) {

    // Success response

    /**
     *
     * @param message
     * @param data
     * @return
     * @param <T>
     */
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
