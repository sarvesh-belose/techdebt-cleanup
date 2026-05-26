package com.enterprise.techdebt.secret;

public class SecretNotFoundException extends RuntimeException {
    public SecretNotFoundException(String ref) {
        super("Secret not found: " + ref);
    }
}
