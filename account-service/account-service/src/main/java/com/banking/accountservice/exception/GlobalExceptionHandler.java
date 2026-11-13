package com.banking.accountservice.exception;

import com.banking.accountservice.response.ApiResponse;
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

/**
 * We will handle these things:
 * Business exceptions.
 * Bean validation failures.
 * Invalid JSON request bodies.
 * Invalid request parameters.
 * Database constraint violations.
 * Unexpected server exceptions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    // 1. Handle all business exceptions
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {

        HttpStatus httpStatus = getHttpStatus(exception.getErrorCode());
        log.warn("business exception : {} - {}", exception.getErrorCode(), exception.getMessage());

        return ResponseEntity.status(httpStatus).body(ApiResponse.failure(exception.getMessage(), exception.getErrorCode(), exception.getMessage()));
    }

    // 2. Handle @Valid request body validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handeValidException(MethodArgumentNotValidException exception) {

        String details = exception.getBindingResult().getFieldErrors().stream().map(ex -> ex.getField() + " : " + ex.getDefaultMessage()).collect(Collectors.joining(", "));

        return ResponseEntity.badRequest().body(ApiResponse.failure(exception.getMessage(), ErrorCode.VALIDATION_FAILED, details));
    }

    // 3. Handle invalid JSON request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(HttpMessageNotReadableException exception) {

        return ResponseEntity.badRequest().body(ApiResponse.failure("Invalid Request Body", ErrorCode.INVALID_REQUEST, "Request body is missing or contains invalid JSON"));

    }

    // 4. Handle invalid query parameters or path variables
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {

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

        log.warn("Database constraint violation", exception);

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

    // 7. Handle all unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception exception) {

        log.error("Unexpected internal server error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.failure(
                        "Something went wrong. Please try again later.",
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "An unexpected internal error occurred"
                )
        );
    }

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


    // Map business error codes to HTTP status codes
    private HttpStatus getHttpStatus(ErrorCode errorCode) {

        return switch (errorCode) {

            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case ACCOUNT_BLOCKED, ACCESS_DENIED -> HttpStatus.FORBIDDEN;

            case INSUFFICIENT_BALANCE, TRANSACTION_LIMIT_EXCEEDED -> HttpStatus.UNPROCESSABLE_ENTITY;

            case ACCOUNT_ALREADY_EXISTS, DATA_CONFLICT -> HttpStatus.CONFLICT;

            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;

            case INTERNAL_SERVER_ERROR, DATABASE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;

            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
