package com.mindcart.backend.dto;

import com.mindcart.backend.entity.Role;
import com.mindcart.backend.entity.User;

/** A list member merged with their public user info, matching the original
 *  `{ ...user, role }` shape returned by the Express API. */
public class MemberDto {
    public String id;
    public String email;
    public String name;
    public String avatarUrl;
    public Role role;

    public MemberDto() {}

    public MemberDto(User user, Role role) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.name = user.getName();
        this.avatarUrl = user.getAvatarUrl();
        this.role = role;
    }
}
