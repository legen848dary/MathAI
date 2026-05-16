package com.insoftu.mathai.ai;

public class AiServiceException extends RuntimeException {

    private final int statusCode;

    public AiServiceException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public AiServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
