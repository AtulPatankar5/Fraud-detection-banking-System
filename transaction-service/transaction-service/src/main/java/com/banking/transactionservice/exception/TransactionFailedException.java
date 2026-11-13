package com.banking.transactionservice.exception;

public class TransactionFailedException extends BusinessException {
    public TransactionFailedException(String message) {
        super(ErrorCode.TRANSACTION_FAILED, message);
    }
}
