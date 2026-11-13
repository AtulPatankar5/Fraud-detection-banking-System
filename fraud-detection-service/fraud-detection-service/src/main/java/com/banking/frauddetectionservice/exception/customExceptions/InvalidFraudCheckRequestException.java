package com.banking.frauddetectionservice.exception.customExceptions;

import com.banking.frauddetectionservice.exception.BusinessException;
import com.banking.frauddetectionservice.exception.ErrorCode;

public class InvalidFraudCheckRequestException extends BusinessException {

    public InvalidFraudCheckRequestException(String message) {
        super(ErrorCode.INVALID_FRAUD_CHECK_REQUEST, message);
    }
}