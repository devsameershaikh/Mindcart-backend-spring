package com.mindcart.backend.util;

import com.mindcart.backend.exception.UnauthorizedException;
import com.mindcart.backend.security.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {

    private AuthUtil() {}

    public static AuthenticatedUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof AuthenticatedUser user)) {
            // Should not happen for routes protected by SecurityConfig, but
            // fail safe rather than NPE if it's ever called somewhere unprotected.
            throw new UnauthorizedException("Missing or invalid auth token");
        }
        return user;
    }
}
