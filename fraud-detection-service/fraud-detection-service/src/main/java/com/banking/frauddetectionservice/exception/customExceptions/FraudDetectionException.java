package com.banking.frauddetectionservice.exception.customExceptions;

import com.banking.frauddetectionservice.exception.BusinessException;
import com.banking.frauddetectionservice.exception.ErrorCode;

public class FraudDetectionException extends BusinessException {

    public FraudDetectionException(String message, Throwable cause) {
        super(ErrorCode.FRAUD_DETECTION_FAILED, message, cause);
    }
}