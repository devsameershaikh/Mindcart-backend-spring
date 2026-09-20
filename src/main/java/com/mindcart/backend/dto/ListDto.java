package com.mindcart.backend.dto;

import com.mindcart.backend.entity.Role;
import com.mindcart.backend.entity.ShoppingList;

import java.time.Instant;
import java.util.List;

public class ListDto {
    public String id;
    public String name;
    public Role role;
    public String ownerId;
    public Instant createdAt;
    public List<ItemDto> items;
    public List<MemberDto> members;

    public ListDto() {}

    public ListDto(ShoppingList list, Role callerRole, List<ItemDto> items, List<MemberDto> members) {
        this.id = list.getId();
        this.name = list.getName();
        this.role = callerRole;
        this.ownerId = list.getOwnerId();
        this.createdAt = list.getCreatedAt();
        this.items = items;
        this.members = members;
    }
}
