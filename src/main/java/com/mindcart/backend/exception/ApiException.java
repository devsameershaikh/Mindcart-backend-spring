package com.mindcart.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all "expected" business errors. Carries an HTTP status
 * and a client-safe message, mirroring the { error: "..." } JSON shape
 * the original Express routes returned.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
