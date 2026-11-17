package com.banking.paymentservice.exception;

import com.banking.paymentservice.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //1. Handle Business Exceptions
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = getHttpStatus(ex.getErrorCode());

        log.warn("Payment business exception: {} - {}",
                ex.getErrorCode(),
                ex.getMessage());

        return ResponseEntity.status(status).body(
                ApiResponse.failure(ex.getMessage(), ex.getErrorCode(), ex.getMessage())
        );
    }

    // 2. Handle @Valid request body validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidException(MethodArgumentNotValidException ex) {

        String details = ex.getBindingResult().
                getFieldErrors()
                .stream().
                map(error -> error.getField() + " : " + error.getDefaultMessage()).
                collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(
                ApiResponse.failure(ex.getMessage(),
                        ErrorCode.VALIDATION_FAILED,
                        details)
        );
    }

    // 3. Handle invalid JSON request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException exception) {

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Invalid Request Body",
                        ErrorCode.INVALID_REQUEST,
                        "Request body is missing or contains invalid JSON"
                )
        );
    }


    // 4. Handle invalid query parameters or path variables
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Invalid parameter value",
                        ErrorCode.INVALID_REQUEST,
                        "Invalid value supplied for parameter: "
                                + exception.getName()
                )
        );
    }

    // 5. Handle database constraint violations
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        log.warn("Payment database constraint violation", exception);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiResponse.failure(
                        "Database constraint violation",
                        ErrorCode.DATA_CONFLICT,
                        "The requested operation conflicts with existing data"
                )
        );
    }

    // 6. Handle Bean Validation constraint violations
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception) {

        String details = exception.getConstraintViolations()
                .stream()
                .map(violation ->
                        violation.getPropertyPath() + ": "
                                + violation.getMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        details
                )
        );
    }

    // 7. Handle method-level validation errors
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException exception) {

        return ResponseEntity.badRequest().body(
                ApiResponse.failure(
                        "Request validation failed",
                        ErrorCode.VALIDATION_FAILED,
                        "One or more request parameters have invalid values"
                )
        );
    }

    // 8. Handle all unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception exception) {

        log.error("Unexpected payment service error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.failure(
                        "Something went wrong. Please try again later.",
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "An unexpected internal error occurred"
                )
        );
    }

    // Map business error codes to HTTP status codes
    private HttpStatus getHttpStatus(ErrorCode errorCode) {

        return switch (errorCode) {

            case PAYMENT_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;

            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;

            case PAYMENT_ALREADY_EXISTS,
                 PAYMENT_ALREADY_PROCESSED,
                 DATA_CONFLICT -> HttpStatus.CONFLICT;

            case PAYMENT_FAILED,
                 INVALID_PAYMENT_STATUS -> HttpStatus.UNPROCESSABLE_ENTITY;

            case PAYMENT_PROVIDER_ERROR -> HttpStatus.BAD_GATEWAY;

            case DATABASE_ERROR,
                 INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;

            case WEBHOOK_SIGNATURE_INVALID,
                 WEBHOOK_EVENT_INVALID,
                 INVALID_REQUEST,
                 VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;

            default -> HttpStatus.BAD_REQUEST;
        };

    }
}