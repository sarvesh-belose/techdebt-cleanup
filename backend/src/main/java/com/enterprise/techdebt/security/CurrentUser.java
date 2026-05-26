package com.enterprise.techdebt.security;

/**
 * Lightweight current-user record. Always non-null when a request has passed authentication.
 */
public record CurrentUser(Long userId, String upn, String email, String displayName) {
}
