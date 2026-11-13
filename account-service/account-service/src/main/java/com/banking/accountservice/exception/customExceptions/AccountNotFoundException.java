package com.banking.accountservice.exception.customExceptions;

import com.banking.accountservice.exception.BusinessException;
import com.banking.accountservice.exception.ErrorCode;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(String message) {
        super(ErrorCode.ACCOUNT_NOT_FOUND, message);

    }
}
