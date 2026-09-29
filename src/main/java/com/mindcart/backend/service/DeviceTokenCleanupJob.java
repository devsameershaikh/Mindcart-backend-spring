package com.mindcart.backend.service;

import com.mindcart.backend.repository.DeviceTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly backstop: removes tokens whose device hasn't checked in for
 * app.push.stale-days (default 60). The app re-registers at least once a day
 * while it is being used, so an active device never ages out.
 */
@Component
public class DeviceTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenCleanupJob.class);

    private final DeviceTokenRepository repository;

    @Value("${app.push.stale-days:60}")
    private int staleDays;

    public DeviceTokenCleanupJob(DeviceTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${app.push.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    public void purgeStale() {
        try {
            int n = repository.deleteStale(Instant.now().minus(Duration.ofDays(staleDays)));
            if (n > 0) log.info("Purged {} stale push token(s) (not seen in {} days)", n, staleDays);
        } catch (Exception e) {
            log.warn("Stale push token purge failed: {}", e.toString());
        }
    }
}