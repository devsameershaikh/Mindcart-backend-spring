package com.mindcart.backend.dto;

import com.mindcart.backend.entity.Item;
import java.math.BigDecimal;
import java.time.Instant;

public class ItemDto {
    public String id;
    public String listId;
    public String name;
    public String category;
    public Integer qty;
    public String unit;
    public BigDecimal price;
    public Boolean checked;
    public Boolean skipped;
    public String note;
    public Instant createdAt;
    public Instant updatedAt;
    public String updatedBy;

    public ItemDto() {}

    public ItemDto(Item item) {
        this.id = item.getId();
        this.listId = item.getListId();
        this.name = item.getName();
        this.category = item.getCategory();
        this.qty = item.getQty();
        this.unit = item.getUnit();
        this.price = item.getPrice();
        this.checked = item.getChecked();
        this.skipped = item.getSkipped();
        this.note = item.getNote();
        this.createdAt = item.getCreatedAt();
        this.updatedAt = item.getUpdatedAt();
        this.updatedBy = item.getUpdatedBy();
    }
}
