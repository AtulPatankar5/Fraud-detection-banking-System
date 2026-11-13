package com.banking.accountservice.exception;

public class AccountBlockedException extends BusinessException {
    public AccountBlockedException(String message) {
        super(ErrorCode.ACCOUNT_BLOCKED, message);
    }
}
