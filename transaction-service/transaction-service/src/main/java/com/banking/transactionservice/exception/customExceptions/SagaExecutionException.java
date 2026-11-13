package com.banking.transactionservice.exception.customExceptions;

import com.banking.transactionservice.exception.BusinessException;
import com.banking.transactionservice.exception.ErrorCode;

public class SagaExecutionException extends BusinessException {
    public SagaExecutionException(String message) {
        super(ErrorCode.SAGA_EXECUTION_FAILED, message);
    }
}
