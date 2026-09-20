package com.mindcart.backend.config;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.namespace.Namespace;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import com.mindcart.backend.security.JwtService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Boots an embedded Socket.IO server (protocol-compatible with the
 * socket.io-client v4 SDK already used by the mobile app) so the client
 * doesn't need to change its transport at all -- only the base URL/port.
 *
 * Auth mirrors the original `io.use((socket, next) => verifySocketToken(...))`
 * middleware: the client connects with `auth: { token }`, and we verify our
 * own session JWT (the same one issued by POST /auth/google) before
 * allowing the handshake to complete.
 */
@org.springframework.context.annotation.Configuration
public class SocketIOConfig {

    private static final Logger log = LoggerFactory.getLogger(SocketIOConfig.class);

    @Value("${app.socketio.port:4001}")
    private int port;

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer(JwtService jwtService) {
        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(port);
        // In production, terminate TLS/WSS at a reverse proxy in front of this port
        // (same as you would for the plain http.Server in the original app).
        config.setOrigin(null); // CORS for socket.io handled at the proxy layer; see README.

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        config.setSocketConfig(socketConfig);

        this.server = new SocketIOServer(config);

        // Equivalent of the Express app's io.use(...) auth middleware: reject
        // the handshake outright if the JWT is missing/invalid/expired,
        // rather than letting an unauthenticated socket connect at all.
        // (addAuthTokenListener lives on SocketIOServer/SocketIONamespace,
        // not on Configuration -- it registers against the default namespace.)
        SocketIONamespace defaultNs = server.getNamespace(Namespace.DEFAULT_NAME);
        defaultNs.addAuthTokenListener((authData, client) -> {
            try {
                String token = extractToken(authData);
                Claims claims = jwtService.verify(token);
                client.set("userId", claims.getSubject());
                return AuthTokenResult.AuthTokenResultSuccess;
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("Rejected socket.io handshake: {}", e.getMessage());
                return new AuthTokenResult(false, "unauthorized");
            }
        });

        // NOTE: netty-socketio isolates exceptions per-connection by default
        // (a bad frame from one client disconnects only that client), so no
        // custom ExceptionListener is required for the "server never stops"
        // requirement. If you want centralized logging of socket-level
        // exceptions, implement com.corundumstudio.socketio.listener.ExceptionListener
        // for your exact netty-socketio version and wire it in with
        // config.setExceptionListener(...) before building the server --
        // the interface has changed shape across versions, so check it
        // against the version pinned in pom.xml.

        return server;
    }

    @SuppressWarnings("unchecked")
    private String extractToken(Object authData) {
        if (authData instanceof Map<?, ?> map) {
            Object token = ((Map<String, Object>) map).get("token");
            if (token != null) return token.toString();
        }
        throw new IllegalArgumentException("Missing token in socket auth payload");
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
