package com.banking.accountservice.exception.customExceptions;

import com.banking.accountservice.exception.BusinessException;
import com.banking.accountservice.exception.ErrorCode;

public class AccountBlockedException extends BusinessException {
    public AccountBlockedException(String message) {
        super(ErrorCode.ACCOUNT_BLOCKED, message);
    }
}
