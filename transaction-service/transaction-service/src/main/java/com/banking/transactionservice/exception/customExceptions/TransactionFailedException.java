package com.banking.transactionservice.exception.customExceptions;

import com.banking.transactionservice.exception.BusinessException;
import com.banking.transactionservice.exception.ErrorCode;

public class TransactionFailedException extends BusinessException {
    public TransactionFailedException(String message) {
        super(ErrorCode.TRANSACTION_FAILED, message);
    }
}
