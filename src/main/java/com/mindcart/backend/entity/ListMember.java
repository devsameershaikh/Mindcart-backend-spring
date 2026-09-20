package com.mindcart.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "list_members", uniqueConstraints = {
        @UniqueConstraint(name = "list_members_list_user_unique", columnNames = {"list_id", "user_id"})
})
public class ListMember {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "list_id", nullable = false)
    private String listId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.READ;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (joinedAt == null) joinedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
