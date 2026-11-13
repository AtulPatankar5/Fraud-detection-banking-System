package com.banking.frauddetectionservice.exception.customExceptions;

import com.banking.frauddetectionservice.exception.BusinessException;
import com.banking.frauddetectionservice.exception.ErrorCode;

public class AccountServiceException extends BusinessException {

    public AccountServiceException(String message, Throwable cause) {
        super(ErrorCode.ACCOUNT_SERVICE_FAILURE, message, cause);
    }
}