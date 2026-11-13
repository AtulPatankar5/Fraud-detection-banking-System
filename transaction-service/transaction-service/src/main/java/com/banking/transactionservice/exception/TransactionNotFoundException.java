package com.banking.transactionservice.exception;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(String messsage) {
        super(ErrorCode.TRANSACTION_NOT_FOUND, messsage);
    }
}
