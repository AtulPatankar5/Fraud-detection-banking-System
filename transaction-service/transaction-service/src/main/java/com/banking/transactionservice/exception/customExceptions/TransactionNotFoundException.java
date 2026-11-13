package com.banking.transactionservice.exception.customExceptions;

import com.banking.transactionservice.exception.BusinessException;
import com.banking.transactionservice.exception.ErrorCode;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(String messsage) {
        super(ErrorCode.TRANSACTION_NOT_FOUND, messsage);
    }
}
