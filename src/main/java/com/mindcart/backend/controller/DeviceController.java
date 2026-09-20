package com.mindcart.backend.controller;

import com.mindcart.backend.dto.RegisterPushTokenRequest;
import com.mindcart.backend.exception.BadRequestException;
import com.mindcart.backend.security.AuthenticatedUser;
import com.mindcart.backend.service.PushNotificationService;
import com.mindcart.backend.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Device push-token registration. Both endpoints sit behind the normal JWT
 * filter (SecurityConfig's `anyRequest().authenticated()`), so no extra
 * config is needed -- the token is always bound to the caller's own user id,
 * never to one supplied in the body.
 */
@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final PushNotificationService pushService;

    public DeviceController(PushNotificationService pushService) {
        this.pushService = pushService;
    }

    // POST /devices/push-token
    @PostMapping("/push-token")
    public Map<String, Object> register(@Valid @RequestBody RegisterPushTokenRequest request) {
        AuthenticatedUser me = AuthUtil.currentUser();

        // Cheap sanity check: reject anything that isn't shaped like an Expo
        // token so a bad client can't fill the table with junk we'd then
        // POST to Expo on every send.
        String token = request.token == null ? "" : request.token.trim();
        if (!token.startsWith("ExponentPushToken[") && !token.startsWith("ExpoPushToken[")) {
            throw new BadRequestException("Not a valid Expo push token");
        }

        pushService.registerDevice(me.getUserId(), token, request.platform,
                request.deviceName, request.appVersion);
        return Map.of("ok", true);
    }

    // DELETE /devices/push-token/{token}  -- called on sign-out
    @DeleteMapping("/push-token/{token}")
    public Map<String, Object> unregister(@PathVariable String token) {
        AuthenticatedUser me = AuthUtil.currentUser();
        pushService.unregisterDevice(me.getUserId(), token);
        return Map.of("ok", true);
    }
}