package com.hmdp.ai;

public class AiCustomerServiceException extends RuntimeException {
    public AiCustomerServiceException(String message) {
        super(message);
    }

    public AiCustomerServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
