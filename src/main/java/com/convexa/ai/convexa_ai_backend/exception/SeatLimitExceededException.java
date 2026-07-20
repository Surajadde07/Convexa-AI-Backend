package com.convexa.ai.convexa_ai_backend.exception;

public class SeatLimitExceededException extends RuntimeException {
    public SeatLimitExceededException(String message) {
        super(message);
    }
}
