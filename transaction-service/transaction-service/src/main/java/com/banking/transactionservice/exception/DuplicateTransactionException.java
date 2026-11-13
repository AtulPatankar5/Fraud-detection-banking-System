package com.banking.transactionservice.exception;

public class DuplicateTransactionException extends BusinessException {
    public DuplicateTransactionException(String message) {
        super(ErrorCode.TRANSACTION_ALREADY_EXISTS, message);
    }
}
