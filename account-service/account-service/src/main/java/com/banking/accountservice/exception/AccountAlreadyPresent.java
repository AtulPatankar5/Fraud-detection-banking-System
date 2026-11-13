package com.banking.accountservice.exception;

public class AccountAlreadyPresent extends BusinessException {

    public AccountAlreadyPresent(String message) {
        super(ErrorCode.ACCOUNT_ALREADY_EXISTS, message);
    }
}
