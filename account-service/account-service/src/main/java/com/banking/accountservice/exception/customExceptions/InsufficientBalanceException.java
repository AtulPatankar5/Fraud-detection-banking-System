package com.banking.accountservice.exception.customExceptions;

import com.banking.accountservice.exception.BusinessException;
import com.banking.accountservice.exception.ErrorCode;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String message) {
        super(ErrorCode.INSUFFICIENT_BALANCE, message);
    }
}
