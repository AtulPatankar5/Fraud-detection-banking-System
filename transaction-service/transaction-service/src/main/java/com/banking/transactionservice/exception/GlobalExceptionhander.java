package com.banking.transactionservice.exception;

import com.banking.transactionservice.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionhander {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionhander.class);

    // 1. Handle all custom business exceptions
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = getHttpStatus(errorCode);
        log.warn("Business exception: {} - {}", errorCode, exception.getMessage());

        return ResponseEntity.status(status).body(ApiResponse.failure(exception.getMessage(), errorCode, exception.getMessage()));

    }

    // 2. Handle DTO validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {

        String details = exception.getBindingResult().getFieldErrors().stream().map(ex -> ex.getField() + " ; " + ex.getDefaultMessage()).reduce((first, second) -> first + "; " + second).orElse("Invalid Request");

        log.warn("Validation failed: {}", details);

        return ResponseEntity.badRequest().body(ApiResponse.failure("Request validation failed", ErrorCode.VALIDATION_FAILED, details));
    }

    // 3. Handle method parameter validation errors
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException exception) {

        log.warn("Method parameter validation failed");

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        "One or more request parameters have invalid values"
                ));
    }

    // 4. Handle invalid JSON request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestBody(
            HttpMessageNotReadableException exception) {

        log.warn("Invalid request body: {}", exception.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        "Invalid request body",
                        ErrorCode.INVALID_REQUEST,
                        "Request body is missing or contains invalid JSON"
                ));
    }

    // 5. Handle invalid path/query parameter types
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        String details = "Invalid value for parameter: "
                + exception.getMessage();

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        "Invalid request parameter",
                        ErrorCode.INVALID_REQUEST,
                        details
                ));
    }

    // 6. Handle constraint violations
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {

        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(
                        "Validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        exception.getMessage()
                ));
    }

    // 7. Handle database constraint violations
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        log.error("Database constraint violation", exception);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "Database constraint violation",
                        ErrorCode.DATA_CONFLICT,
                        "The requested operation conflicts with existing data"
                ));
    }

    // 8. Handle unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception exception) {

        log.error("Unexpected error occurred", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "An unexpected error occurred",
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "Please try again later"
                ));
    }


    // HTTP status mapping
    private HttpStatus getHttpStatus(ErrorCode errorCode) {

        return switch (errorCode) {

            case TRANSACTION_NOT_FOUND, ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case ACCOUNT_BLOCKED, ACCESS_DENIED -> HttpStatus.FORBIDDEN;

            case INSUFFICIENT_BALANCE, TRANSACTION_LIMIT_EXCEEDED, INVALID_TRANSACTION_AMOUNT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;

            case TRANSACTION_ALREADY_EXISTS, DATA_CONFLICT -> HttpStatus.CONFLICT;

            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;

            case INVALID_TRANSACTION, INVALID_REQUEST, VALIDATION_FAILED, MISSING_REQUIRED_FIELD ->
                    HttpStatus.BAD_REQUEST;

            case SAGA_EXECUTION_FAILED, SAGA_COMPENSATION_FAILED, SAGA_TIMEOUT, TRANSACTION_FAILED, DATABASE_ERROR,
                 INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
