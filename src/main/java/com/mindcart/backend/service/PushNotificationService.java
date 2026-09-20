package com.mindcart.backend.service;

import com.mindcart.backend.entity.DeviceToken;
import com.mindcart.backend.repository.DeviceTokenRepository;
//import io.sentry.Sentry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * The one place the backend sends push notifications.
 *
 * Call it from anywhere:
 *
 *   pushService.sendToUser(userId, PushMessage.builder()
 *           .title("New invite")
 *           .body("Asha invited you to \"Groceries\"")
 *           .type("invite:received")
 *           .data("inviteId", invite.getId())
 *           .channel("invites")
 *           .build());
 *
 * Design notes that matter:
 *
 *  - EVERY public send method is @Async and swallows its own failures. A
 *    push is a courtesy on top of the socket event; it must never roll back
 *    or fail the HTTP request that triggered it. The DB write has already
 *    committed by the time we get here.
 *
 *  - Expo's /push/send accepts up to 100 messages per request, so we chunk.
 *
 *  - Expo replies per-message. A `DeviceNotRegistered` error means the app
 *    was uninstalled or the token was rotated -- we delete that row
 *    immediately. Without this, dead tokens accumulate forever and every
 *    send gets slower and noisier.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private static final String EXPO_SEND_URL = "https://exp.host/--/api/v2/push/send";
    private static final int MAX_MESSAGES_PER_REQUEST = 100;

    private final DeviceTokenRepository deviceTokenRepository;
    private final RestClient restClient;

    @Value("${app.push.enabled:true}")
    private boolean enabled;

    /**
     * Optional. Only needed if you have enabled "Enhanced Security for Push
     * Notifications" in your Expo account; unauthenticated sends work fine
     * otherwise.
     */
    @Value("${app.push.expo-access-token:}")
    private String expoAccessToken;

    public PushNotificationService(DeviceTokenRepository deviceTokenRepository,
                                   RestClient.Builder restClientBuilder) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.restClient = restClientBuilder.build();
    }

    // ------------------------------------------------------------------
    // Device registration
    // ------------------------------------------------------------------

    /**
     * Upsert. If the token already exists under a DIFFERENT user (shared or
     * resold device), it is reassigned rather than duplicated -- see the
     * comment on the entity.
     */
    @Transactional
    public void registerDevice(String userId, String token, String platform,
                               String deviceName, String appVersion) {
        DeviceToken row = deviceTokenRepository.findById(token).orElseGet(DeviceToken::new);
        row.setToken(token);
        row.setUserId(userId);
        row.setPlatform(platform);
        row.setDeviceName(deviceName);
        row.setAppVersion(appVersion);
        row.setLastSeenAt(java.time.Instant.now());
        deviceTokenRepository.save(row);
    }

    /**
     * Only removes the row if it actually belongs to the caller -- otherwise
     * anyone who learned a token string could silently unsubscribe someone
     * else's device.
     */
    @Transactional
    public void unregisterDevice(String userId, String token) {
        deviceTokenRepository.findById(token)
                .filter(row -> row.getUserId().equals(userId))
                .ifPresent(deviceTokenRepository::delete);
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    @Async("pushExecutor")
    public void sendToUser(String userId, PushMessage message) {
        log.info("message :{}",message.toString());
        sendToUsers(List.of(userId), message);
    }

    @Async("pushExecutor")
    public void sendToUsers(Collection<String> userIds, PushMessage message) {
        if (!enabled || userIds == null || userIds.isEmpty()) return;
        try {
            // TODO: new remove hardcore
            List<DeviceToken> devices = deviceTokenRepository.findByUserIdIn(userIds);
            if (devices.isEmpty()) {
                log.debug("No registered devices for users {} -- skipping push", userIds);
                return;
            }
            List<String> tokens = devices.stream().map(DeviceToken::getToken).toList();
            dispatch(tokens, message);
        } catch (Exception e) {
            // Deliberately terminal: nothing upstream is waiting on this.
            log.warn("Push send failed for users {}: {}", userIds, e.toString());
//            Sentry.captureException(e);
        }
    }

    private void dispatch(List<String> tokens, PushMessage message) {
        for (int start = 0; start < tokens.size(); start += MAX_MESSAGES_PER_REQUEST) {
            List<String> chunk = tokens.subList(start, Math.min(start + MAX_MESSAGES_PER_REQUEST, tokens.size()));
            List<Map<String, Object>> payload = new ArrayList<>(chunk.size());
            for (String token : chunk) {
                payload.add(message.toExpoMessage(token));
            }
            postChunk(chunk, payload);
        }
    }

    @SuppressWarnings("unchecked")
    private void postChunk(List<String> tokens, List<Map<String, Object>> payload) {
        Map<String, Object> response;
        try {
            var spec = restClient.post()
                    .uri(EXPO_SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("accept-encoding", "gzip, deflate");
            if (expoAccessToken != null && !expoAccessToken.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + expoAccessToken);
            }
            response = spec.body(payload).retrieve().body(Map.class);
        } catch (Exception e) {
            log.warn("Expo push request failed ({} tokens): {}", tokens.size(), e.toString());
//            Sentry.captureException(e);
            return;
        }

        if (response == null) return;
        Object dataObj = response.get("data");
        if (!(dataObj instanceof List<?> tickets)) return;

        List<String> deadTokens = new ArrayList<>();
        for (int i = 0; i < tickets.size() && i < tokens.size(); i++) {
            if (!(tickets.get(i) instanceof Map<?, ?> ticket)) continue;
            if (!"error".equals(ticket.get("status"))) continue;

            Object details = ticket.get("details");
            String code = (details instanceof Map<?, ?> d) ? String.valueOf(d.get("error")) : null;
            if ("DeviceNotRegistered".equals(code)) {
                // App uninstalled or token rotated -- prune it now.
                deadTokens.add(tokens.get(i));
            } else {
                log.warn("Expo rejected a push: {} ({})", ticket.get("message"), code);
            }
        }
        if (!deadTokens.isEmpty()) {
            pruneTokens(deadTokens);
        }
    }

    /**
     * REQUIRES_NEW because this runs on the async executor, outside any
     * caller transaction -- it needs its own.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pruneTokens(List<String> tokens) {
        try {
            deviceTokenRepository.deleteByTokenIn(tokens);
            log.info("Pruned {} unregistered push token(s)", tokens.size());
        } catch (Exception e) {
            log.warn("Failed to prune dead push tokens: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Message model
    // ------------------------------------------------------------------

    /**
     * An Expo push message. `type` lands in data.type, which is what the
     * app's notificationService routes on -- keep the values in sync with
     * PUSH_TYPES in notificationService.js.
     */
    public static final class PushMessage {

        private String title;
        private String body;
        private String type;
        private String channelId = "default";
        private String sound = "default";
        private String priority = "high";
        private final Map<String, Object> data = new LinkedHashMap<>();

        public static PushMessage builder() { return new PushMessage(); }

        public PushMessage title(String title) { this.title = title; return this; }
        public PushMessage body(String body) { this.body = body; return this; }
        public PushMessage type(String type) { this.type = type; return this; }
        public PushMessage channel(String channelId) { this.channelId = channelId; return this; }
        public PushMessage silent() { this.sound = null; this.priority = "normal"; return this; }

        public PushMessage data(String key, Object value) {
            if (value != null) this.data.put(key, value);
            return this;
        }

        public PushMessage build() { return this; }

        Map<String, Object> toExpoMessage(String token) {
            Map<String, Object> payload = new LinkedHashMap<>(data);
            if (type != null) payload.put("type", type);

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("to", token);
            msg.put("title", title);
            msg.put("body", body);
            msg.put("data", payload);
            msg.put("priority", priority);
            msg.put("channelId", channelId);
            if (sound != null) msg.put("sound", sound);
            // Collapse repeats of the same kind of event so a burst of
            // invites doesn't stack five identical-looking notifications.
            if (type != null) msg.put("collapseKey", type);
            return msg;
        }
    }
}