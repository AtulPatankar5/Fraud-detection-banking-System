package com.banking.transactionservice.exception;

public class InvalidTransactionException extends BusinessException {
    public InvalidTransactionException(String message) {
        super(ErrorCode.INVALID_TRANSACTION, message);
    }
}
