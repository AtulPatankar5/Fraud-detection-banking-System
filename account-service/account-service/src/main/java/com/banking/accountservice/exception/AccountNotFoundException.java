package com.banking.accountservice.exception;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(String message) {
        super(ErrorCode.ACCOUNT_NOT_FOUND, message);

    }
}
