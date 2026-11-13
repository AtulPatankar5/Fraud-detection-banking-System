package com.banking.accountservice.exception.customExceptions;

import com.banking.accountservice.exception.BusinessException;
import com.banking.accountservice.exception.ErrorCode;

public class AccountAlreadyPresent extends BusinessException {

    public AccountAlreadyPresent(String message) {
        super(ErrorCode.ACCOUNT_ALREADY_EXISTS, message);
    }
}
