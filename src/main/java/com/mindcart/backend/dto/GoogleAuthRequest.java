package com.mindcart.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class GoogleAuthRequest {
    @NotBlank(message = "idToken is required")
    public String idToken;
}
