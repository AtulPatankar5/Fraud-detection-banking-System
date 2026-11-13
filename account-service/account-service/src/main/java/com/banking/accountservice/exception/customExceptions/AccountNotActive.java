package com.banking.accountservice.exception.customExceptions;

import com.banking.accountservice.exception.BusinessException;
import com.banking.accountservice.exception.ErrorCode;

public class AccountNotActive extends BusinessException {

    public AccountNotActive(String message) {
        super(ErrorCode.ACCOUNT_INACTIVE, message);
    }
}
