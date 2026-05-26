package com.enterprise.techdebt.copilot;

public class CopilotException extends RuntimeException {
    public CopilotException(String message) {
        super(message);
    }

    public CopilotException(String message, Throwable cause) {
        super(message, cause);
    }
}
