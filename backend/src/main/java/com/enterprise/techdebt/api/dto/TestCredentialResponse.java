package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.credentials.ValidationResult;

public record TestCredentialResponse(boolean ok, String status, String message) {
    public static TestCredentialResponse from(ValidationResult r) {
        return new TestCredentialResponse(r.ok(), r.status(), r.message());
    }
}
