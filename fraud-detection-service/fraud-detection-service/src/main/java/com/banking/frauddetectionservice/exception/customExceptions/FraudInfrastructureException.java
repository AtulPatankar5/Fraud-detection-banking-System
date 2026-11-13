package com.banking.frauddetectionservice.exception.customExceptions;


import com.banking.frauddetectionservice.exception.BusinessException;
import com.banking.frauddetectionservice.exception.ErrorCode;

public class FraudInfrastructureException extends BusinessException {

    public FraudInfrastructureException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
