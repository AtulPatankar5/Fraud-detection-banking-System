package com.banking.accountservice.exception;

public class AccountNotActive extends BusinessException {

    public AccountNotActive(String message) {
        super(ErrorCode.ACCOUNT_INACTIVE, message);
    }
}
