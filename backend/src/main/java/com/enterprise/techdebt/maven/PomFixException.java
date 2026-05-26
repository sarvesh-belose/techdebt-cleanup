package com.enterprise.techdebt.maven;

public class PomFixException extends RuntimeException {
    public PomFixException(String message) {
        super(message);
    }

    public PomFixException(String message, Throwable cause) {
        super(message, cause);
    }
}
