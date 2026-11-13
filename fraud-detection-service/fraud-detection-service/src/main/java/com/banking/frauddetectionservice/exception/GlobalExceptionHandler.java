package com.banking.frauddetectionservice.exception;

import com.banking.frauddetectionservice.response.ApiResponse;
import feign.FeignException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle all custom business exceptions
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex
    ) {

        HttpStatus status = getHttpStatus(ex.getErrorCode());

        log.warn("Business exception: {} - {}",
                ex.getErrorCode(), ex.getMessage());

        return ResponseEntity.status(status).body(
                ApiResponse.failure(
                        ex.getMessage(),
                        ex.getErrorCode(),
                        ex.getMessage()
                )
        );
    }

    // 2. Handle request body validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {

        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Request validation failed: {}", details);

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        details
                )
        );
    }

    // 3. Handle method and parameter validation errors
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidationException(
            HandlerMethodValidationException ex
    ) {

        log.warn("Method validation failed: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        "One or more request parameters are invalid"
                )
        );
    }

    // 4. Handle invalid JSON or unreadable request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
            HttpMessageNotReadableException ex
    ) {

        log.warn("Invalid request body: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Invalid request body",
                        ErrorCode.INVALID_REQUEST,
                        "Request body is missing or contains invalid JSON"
                )
        );
    }

    // 5. Handle invalid path variables and query parameters
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {

        String details = "Invalid value for parameter: " + ex.getName();

        log.warn("Request parameter type mismatch: {}", details);

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Invalid request parameter",
                        ErrorCode.INVALID_REQUEST,
                        details
                )
        );
    }

    // 6. Handle constraint violations
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex
    ) {

        String details = ex.getConstraintViolations()
                .stream()
                .map(violation ->
                        violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", details);

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        details
                )
        );
    }

    // 7. Handle Feign client failures that reach REST controllers
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(
            FeignException ex
    ) {

        log.error("Downstream service communication failed", ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiResponse.failure(
                        "Downstream service unavailable",
                        ErrorCode.ACCOUNT_SERVICE_FAILURE,
                        "Unable to communicate with the dependent service"
                )
        );
    }

    // 8. Handle database and Redis data access failures
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException ex
    ) {

        log.error("Database or data access operation failed", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.failure(
                        "Data access operation failed",
                        ErrorCode.DATABASE_ERROR,
                        "An error occurred while accessing persistent data"
                )
        );
    }

    // 9. Handle unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex
    ) {

        log.error("Unexpected application error", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.failure(
                        "An unexpected error occurred",
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "Please contact support if the problem persists"
                )
        );
    }

    // Map application error codes to HTTP status codes
    private HttpStatus getHttpStatus(ErrorCode errorCode) {

        return switch (errorCode) {

            case INVALID_FRAUD_CHECK_REQUEST,
                 INVALID_REQUEST,
                 VALIDATION_FAILED,
                 MISSING_REQUIRED_FIELD -> HttpStatus.BAD_REQUEST;

            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;

            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;

            case FRAUD_RULE_VIOLATION,
                 SUSPICIOUS_TRANSACTION,
                 INVALID_TRANSACTION_AMOUNT -> HttpStatus.UNPROCESSABLE_ENTITY;

            case DATA_CONFLICT -> HttpStatus.CONFLICT;

            case ACCOUNT_SERVICE_FAILURE,
                 REDIS_OPERATION_FAILED,
                 KAFKA_PUBLISH_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;

            case FRAUD_DETECTION_FAILED,
                 FRAUD_CHECK_NOT_COMPLETED,
                 RISK_SCORE_CALCULATION_FAILED,
                 INVALID_RISK_SCORE,
                 TRANSACTION_NOT_FOUND,
                 INVALID_TRANSACTION,
                 DATABASE_ERROR,
                 INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;

            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
