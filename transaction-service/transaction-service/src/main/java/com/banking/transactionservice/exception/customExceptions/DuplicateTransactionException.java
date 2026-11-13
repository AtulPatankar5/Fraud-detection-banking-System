package com.banking.transactionservice.exception.customExceptions;

import com.banking.transactionservice.exception.BusinessException;
import com.banking.transactionservice.exception.ErrorCode;

public class DuplicateTransactionException extends BusinessException {
    public DuplicateTransactionException(String message) {
        super(ErrorCode.TRANSACTION_ALREADY_EXISTS, message);
    }
}
