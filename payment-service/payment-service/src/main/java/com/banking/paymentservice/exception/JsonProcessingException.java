package com.banking.paymentservice.exception;

public class JsonProcessingException extends BusinessException {
    public JsonProcessingException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
