package com.mindcart.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterPushTokenRequest {

    @NotBlank
    @Size(max = 255)
    public String token;

    @Size(max = 16)
    public String platform;

    @Size(max = 255)
    public String deviceName;

    @Size(max = 32)
    public String appVersion;
}