package com.mindcart.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Used both for "genuinely doesn't exist" and, deliberately, for
 * "exists but you have no access to it" -- same as the original API,
 * which returns 404 rather than 403 in the latter case so an attacker
 * probing ids can't distinguish "not yours" from "doesn't exist".
 */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
