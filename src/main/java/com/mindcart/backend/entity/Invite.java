package com.mindcart.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "invites")
public class Invite {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "list_id")
    private String listId; // null when inviteAllLists = true

    @Column(name = "invite_all_lists", nullable = false)
    private Boolean inviteAllLists = false;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_id")
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.READ;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status = InviteStatus.PENDING;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public Boolean getInviteAllLists() { return inviteAllLists; }
    public void setInviteAllLists(Boolean inviteAllLists) { this.inviteAllLists = inviteAllLists; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public InviteStatus getStatus() { return status; }
    public void setStatus(InviteStatus status) { this.status = status; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
