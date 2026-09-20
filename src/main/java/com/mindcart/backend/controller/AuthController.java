package com.mindcart.backend.controller;

import com.mindcart.backend.dto.AuthResponse;
import com.mindcart.backend.dto.GoogleAuthRequest;
import com.mindcart.backend.dto.UserPublicDto;
import com.mindcart.backend.exception.BadRequestException;
import com.mindcart.backend.exception.TooManyRequestsException;
import com.mindcart.backend.security.RateLimiter;
import com.mindcart.backend.service.AuthService;
import com.mindcart.backend.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    @Value("${app.rate-limit.google-login.capacity:10}")
    private int loginCapacity;
    @Value("${app.rate-limit.google-login.refill-per-minute:10}")
    private int loginRefillPerMinute;

    public AuthController(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    // POST /auth/google { idToken } -> { token, user }
    // Client gets a Google idToken from expo-auth-session and sends it here
    // once; we verify it server-side, upsert the user row, and hand back our
    // own long-lived session JWT so the app never has to touch Google again
    // per-request. Rate-limited per client IP to blunt token-verification
    // abuse / credential stuffing against this endpoint.
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request, HttpServletRequest httpRequest) {
        log.info("verify token");

        String ip = RateLimiter.CLIENT_IP.apply(httpRequest);
        if (!rateLimiter.tryConsume("login:" + ip, loginCapacity, loginRefillPerMinute)) {
            throw new TooManyRequestsException("Too many sign-in attempts. Please try again shortly.");
        }
        if (request.idToken == null || request.idToken.isBlank()) {
            throw new BadRequestException("idToken is required");
        }
        return authService.signInWithGoogle(request.idToken);
    }

    @GetMapping("/me")
    public Map<String, UserPublicDto> me() {
        String userId = AuthUtil.currentUser().getUserId();
        return Map.of("user", authService.me(userId));
    }
}
