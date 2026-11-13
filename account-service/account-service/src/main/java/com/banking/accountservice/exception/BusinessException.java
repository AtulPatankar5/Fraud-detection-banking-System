package com.banking.accountservice.exception;

import lombok.Getter;

/**
 * Why extend RuntimeException?
 * - No need to declare throws in every service method.
 * - Spring can propagate the exception to @RestControllerAdvice.
 * - Business exceptions can trigger transaction rollback by default when thrown inside a Spring-managed transaction.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
