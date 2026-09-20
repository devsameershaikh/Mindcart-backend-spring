package com.mindcart.backend.dto;

import com.mindcart.backend.entity.User;

/** Public-safe user projection - never exposes googleId. */
public class UserPublicDto {
    public String id;
    public String email;
    public String name;
    public String avatarUrl;

    public UserPublicDto() {}

    public UserPublicDto(User u) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.name = u.getName();
        this.avatarUrl = u.getAvatarUrl();
    }
}
