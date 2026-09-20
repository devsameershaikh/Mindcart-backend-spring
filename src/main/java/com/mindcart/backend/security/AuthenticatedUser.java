package com.mindcart.backend.security;

/** Minimal principal carried in the SecurityContext for the lifetime of a request. */
public class AuthenticatedUser {
    private final String userId;
    private final String email;

    public AuthenticatedUser(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public String getUserId() { return userId; }
    public String getEmail() { return email; }
}
