package com.mindcart.backend.repository;

import com.mindcart.backend.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    List<DeviceToken> findByUserId(String userId);

    List<DeviceToken> findByUserIdIn(Collection<String> userIds);

    void deleteByTokenIn(Collection<String> tokens);
}