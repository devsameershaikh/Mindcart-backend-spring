package com.mindcart.backend.dto;

import com.mindcart.backend.entity.Invite;
import com.mindcart.backend.entity.InviteStatus;
import com.mindcart.backend.entity.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
public class InviteDto {
    public String id;
    public String listId;
    public Boolean inviteAllLists;
    public String senderId;
    public UserPublicDto sender;
    public String recipientEmail;
    public String recipientId;
    public Role role;
    public InviteStatus status;
    public Instant createdAt;
    public Instant respondedAt;
    public  String listName;

    public InviteDto() {}

    public void setListName(String listName) {
        this.listName = listName;
    }

    public static InviteDto from(Invite invite) {
        InviteDto dto = new InviteDto();
        dto.id = invite.getId();
        dto.listId = invite.getListId();
        dto.inviteAllLists = invite.getInviteAllLists();
        dto.senderId = invite.getSenderId();
        dto.recipientEmail = invite.getRecipientEmail();
        dto.recipientId = invite.getRecipientId();
        dto.role = invite.getRole();
        dto.status = invite.getStatus();
        dto.createdAt = invite.getCreatedAt();
        dto.respondedAt = invite.getRespondedAt();
//        dto.listName=null;
        return dto;
    }
}
