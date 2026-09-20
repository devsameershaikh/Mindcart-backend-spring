package com.mindcart.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * The original Express app used `app.use(cors())` with no options, which
 * reflects and allows ANY origin. That's fine for a public read-only API,
 * but here every route carries a bearer token and mutates private data, so
 * an unrestricted origin allowlist means any website in a victim's browser
 * could call this API using a token it stole another way, or made CSRF-like
 * requests easier to stage. This config requires an explicit, operator
 * configured allowlist (CORS_ALLOWED_ORIGINS) instead.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    private List<String> allowedOrigins;

    @PostConstruct
    void init() {
        allowedOrigins = Arrays.stream(allowedOriginsRaw == null ? new String[0] : allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (allowedOrigins.isEmpty()) {
            log.warn("CORS_ALLOWED_ORIGINS is not set. No browser-based origin will be able to call this API " +
                    "until it is configured (native mobile clients are unaffected, since CORS only applies to browsers).");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false); // auth is via bearer header, not cookies
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
