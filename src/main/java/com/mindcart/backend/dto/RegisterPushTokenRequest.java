package com.mindcart.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterPushTokenRequest {

    @NotBlank
    @Size(max = 255)
    public String token;

    /** Stable device id from the app (optional: old app versions omit it). */
    @Size(max = 64)
    public String deviceId;

    @Size(max = 16)
    public String platform;

    @Size(max = 255)
    public String deviceName;

    @Size(max = 32)
    public String appVersion;
}