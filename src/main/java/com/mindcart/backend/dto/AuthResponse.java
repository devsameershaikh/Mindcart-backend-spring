package com.mindcart.backend.dto;

public class AuthResponse {
    public String token;
    public UserPublicDto user;

    public AuthResponse(String token, UserPublicDto user) {
        this.token = token;
        this.user = user;
    }
}
