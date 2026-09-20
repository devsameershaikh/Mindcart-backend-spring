package com.mindcart.backend.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mindcart.backend.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * Hardened replacement for the original google-auth-library based check.
 *
 * Improvements over the original Node implementation:
 *  - Refuses to start if no client id is configured, rather than silently
 *    accepting tokens for an empty audience list.
 *  - Explicitly rejects unverified Google accounts (email_verified=false):
 *    the original code never checked this, so a Google account with an
 *    unverified email (e.g. one created with an email the attacker doesn't
 *    actually control) could still be used to sign in / claim invites sent
 *    to that address.
 *  - google-auth-library's GoogleIdTokenVerifier already validates
 *    signature, expiry, issuer ("accounts.google.com" / "https://accounts.google.com")
 *    and audience for us -- we just configure it strictly and add the
 *    extra email-verification check on top.
 */
@Component
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    @Value("${app.google.client-ids}")
    private String clientIdsRaw;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        List<String> clientIds = Arrays.stream(clientIdsRaw == null ? new String[0] : clientIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (clientIds.isEmpty()) {
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_IDS is not set. Configure the OAuth client id(s) issued to your " +
                    "app (one per platform) so Google Sign-In tokens can be verified.");
        }
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(clientIds)
                .build();
    }

    public GoogleProfile verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Google token verification failed");
        }
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            log.warn("Google id token verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Google token verification failed");
        }
        if (idToken == null) {
            throw new UnauthorizedException("Google token verification failed");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        Boolean emailVerified = payload.getEmailVerified();
        if (emailVerified == null || !emailVerified) {
            // Do not allow sign-in / auto-link of an unverified email address.
            throw new UnauthorizedException("Google account email is not verified");
        }
        String sub = payload.getSubject();
        String email = payload.getEmail();
        if (sub == null || email == null) {
            throw new UnauthorizedException("Invalid Google token payload");
        }

        GoogleProfile profile = new GoogleProfile();
        profile.googleId = sub;
        profile.email = email.toLowerCase();
        profile.name = (String) payload.get("name");
        profile.avatarUrl = (String) payload.get("picture");
        return profile;
    }

    public static class GoogleProfile {
        public String googleId;
        public String email;
        public String name;
        public String avatarUrl;
    }
}
