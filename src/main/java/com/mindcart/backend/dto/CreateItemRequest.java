package com.mindcart.backend.dto;

public class CreateItemRequest {
    public String id; // optional client-generated id (offline-first clients)
    public String name;
    public String category;
    public String unit;
    public Object price; // validated/normalized manually, same as the original (string | number | null)
}
