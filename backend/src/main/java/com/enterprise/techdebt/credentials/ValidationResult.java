package com.enterprise.techdebt.credentials;

/**
 * Outcome of a live credential check.
 *
 * @param ok      true when the upstream accepted the credentials
 * @param status  short machine-readable status code
 *                ("OK" / "BAD_FORMAT" / "BAD_CREDENTIALS" / "UNREACHABLE" / "UPSTREAM_ERROR")
 * @param message human-readable detail surfaced verbatim in the UI
 */
public record ValidationResult(boolean ok, String status, String message) {

    public static ValidationResult ok(String message) {
        return new ValidationResult(true, "OK", message);
    }

    public static ValidationResult badFormat(String message) {
        return new ValidationResult(false, "BAD_FORMAT", message);
    }

    public static ValidationResult badCredentials(String message) {
        return new ValidationResult(false, "BAD_CREDENTIALS", message);
    }

    public static ValidationResult unreachable(String message) {
        return new ValidationResult(false, "UNREACHABLE", message);
    }

    public static ValidationResult upstreamError(String message) {
        return new ValidationResult(false, "UPSTREAM_ERROR", message);
    }
}
