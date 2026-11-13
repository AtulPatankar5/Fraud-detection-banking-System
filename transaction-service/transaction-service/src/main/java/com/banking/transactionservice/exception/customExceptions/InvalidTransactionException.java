package com.banking.transactionservice.exception.customExceptions;

import com.banking.transactionservice.exception.BusinessException;
import com.banking.transactionservice.exception.ErrorCode;

public class InvalidTransactionException extends BusinessException {
    public InvalidTransactionException(String message) {
        super(ErrorCode.INVALID_TRANSACTION, message);
    }
}
