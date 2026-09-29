package com.mindcart.backend.repository;

import com.mindcart.backend.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    List<DeviceToken> findByUserId(String userId);

    List<DeviceToken> findByUserIdOrderByLastSeenAtDesc(String userId);

    List<DeviceToken> findByUserIdIn(Collection<String> userIds);

    void deleteByTokenIn(Collection<String> tokens);

    /** Same physical device, new token (reinstall / clear-data / new owner). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DeviceToken d where d.deviceId = :deviceId and d.token <> :token")
    int deleteOtherTokensOfDevice(@Param("deviceId") String deviceId, @Param("token") String token);

    /** Old app builds without deviceId: same user + platform + device name. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DeviceToken d where d.userId = :userId and d.platform = :platform "
            + "and d.deviceName = :deviceName and d.deviceId is null and d.token <> :token")
    int deleteLegacyDuplicates(@Param("userId") String userId,
                               @Param("platform") String platform,
                               @Param("deviceName") String deviceName,
                               @Param("token") String token);

    /** Nightly cleanup of devices that have not checked in for a long time. */
    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.lastSeenAt < :cutoff")
    int deleteStale(@Param("cutoff") Instant cutoff);

    /** Used by the receipt checker (runs outside any caller transaction). */
    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    int bulkDeleteByTokens(@Param("tokens") Collection<String> tokens);
}