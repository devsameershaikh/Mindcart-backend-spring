package com.mindcart.backend.service;

import com.mindcart.backend.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Second half of Expo's delivery flow.
 *
 * A "ticket" ({status: ok}) only means Expo ACCEPTED the message. Whether
 * FCM/APNs could actually deliver it -- including "this app was uninstalled"
 * (DeviceNotRegistered) -- shows up in the RECEIPT, available ~15 minutes
 * later. Checking only tickets is why dead tokens linger and the same phone
 * ends up with old + new rows.
 *
 * Pending tickets are held in memory. A restart loses at most ~15 minutes of
 * them; the nightly stale-token purge is the backstop for those.
 * (Runs on a single instance; if you scale out, move the queue to the DB.)
 */
@Service
public class PushReceiptService {

    private static final Logger log = LoggerFactory.getLogger(PushReceiptService.class);

    private static final String EXPO_RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";
    private static final Duration MIN_AGE = Duration.ofMinutes(15);   // Expo: receipts ready after ~15 min
    private static final Duration MAX_AGE = Duration.ofHours(24);     // Expo drops receipts after 24 h
    private static final int MAX_IDS_PER_REQUEST = 1000;
    private static final int MAX_QUEUE = 50_000;

    private record Pending(String ticketId, String token, Instant at) {}

    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final DeviceTokenRepository deviceTokenRepository;
    private final RestClient restClient;

    @Value("${app.push.enabled:true}")
    private boolean enabled;

    @Value("${app.push.expo-access-token:}")
    private String expoAccessToken;

    public PushReceiptService(DeviceTokenRepository deviceTokenRepository,
                              RestClient.Builder restClientBuilder) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.restClient = restClientBuilder.build();
    }

    public void track(String ticketId, String token) {
        if (pending.size() < MAX_QUEUE) {
            pending.add(new Pending(ticketId, token, Instant.now()));
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.push.receipt-check-ms:300000}")
    @SuppressWarnings("unchecked")
    public void checkReceipts() {
        if (!enabled || pending.isEmpty()) return;

        Instant now = Instant.now();
        Instant ready = now.minus(MIN_AGE);
        Instant expired = now.minus(MAX_AGE);

        List<Pending> batch = new ArrayList<>();
        for (Iterator<Pending> it = pending.iterator(); it.hasNext() && batch.size() < MAX_IDS_PER_REQUEST; ) {
            Pending p = it.next();
            if (p.at().isBefore(expired)) { it.remove(); continue; }      // too old, receipt is gone
            if (p.at().isBefore(ready)) { batch.add(p); it.remove(); }
        }
        if (batch.isEmpty()) return;

        try {
            var spec = restClient.post()
                    .uri(EXPO_RECEIPTS_URL)
                    .contentType(MediaType.APPLICATION_JSON);
            if (expoAccessToken != null && !expoAccessToken.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + expoAccessToken);
            }
            Map<String, Object> response = spec
                    .body(Map.of("ids", batch.stream().map(Pending::ticketId).toList()))
                    .retrieve().body(Map.class);

            Object dataObj = response == null ? null : response.get("data");
            if (!(dataObj instanceof Map<?, ?> receipts)) return;

            Set<String> dead = new HashSet<>();
            for (Pending p : batch) {
                if (!(receipts.get(p.ticketId()) instanceof Map<?, ?> r)) continue;
                if (!"error".equals(r.get("status"))) continue;
                Object details = r.get("details");
                String code = (details instanceof Map<?, ?> d) ? String.valueOf(d.get("error")) : null;
                if ("DeviceNotRegistered".equals(code)) {
                    dead.add(p.token());
                } else {
                    log.warn("Expo receipt error for a push: {} ({})", r.get("message"), code);
                }
            }
            if (!dead.isEmpty()) {
                int n = deviceTokenRepository.bulkDeleteByTokens(dead);
                log.info("Receipts: pruned {} unregistered push token(s)", n);
            }
        } catch (Exception e) {
            log.warn("Expo receipt check failed ({} tickets): {}", batch.size(), e.toString());
            batch.forEach(pending::add);   // try again next run (dropped after 24 h)
        }
    }
}