package com.banking.transactionservice.exception;

public class SagaExecutionException extends BusinessException {
    public SagaExecutionException(String message) {
        super(ErrorCode.SAGA_EXECUTION_FAILED, message);
    }
}
