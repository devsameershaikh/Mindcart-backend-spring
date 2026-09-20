package com.mindcart.backend.dto;

public class InviteRequest {
    public String recipientEmail;
    public String role; // "READ" | "WRITE"
    public String listId; // required unless allLists = true
    public Boolean allLists;
}
