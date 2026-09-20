package com.mindcart.backend.service;

import com.mindcart.backend.dto.CreateItemRequest;
import com.mindcart.backend.dto.ItemDto;
import com.mindcart.backend.entity.Item;
import com.mindcart.backend.entity.Role;
import com.mindcart.backend.exception.BadRequestException;
import com.mindcart.backend.exception.NotFoundException;
import com.mindcart.backend.repository.ItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ItemService {

    private static final Set<String> ALLOWED_UPDATE_FIELDS =
            Set.of("name", "category", "unit", "price", "qty", "checked", "skipped", "note");

    private final ItemRepository itemRepository;
    private final PermissionService permissionService;
    private final RealtimeService realtimeService;

    public ItemService(ItemRepository itemRepository, PermissionService permissionService,
                        RealtimeService realtimeService) {
        this.itemRepository = itemRepository;
        this.permissionService = permissionService;
        this.realtimeService = realtimeService;
    }

    // POST /:listId/items { id?, name, category, unit, price }
    //
    // Same offline-sync idempotency as list creation: `id` is the
    // client-generated permanent id. This MUST be honored -- if the server
    // ever minted its own id instead, the id the client optimistically
    // rendered under and the id actually saved under would diverge, and the
    // client would show the item twice. A duplicate create for an id that
    // already landed (a retried request after a dropped response) is
    // treated as success rather than an error.
    @Transactional
    public ItemDto createItem(String userId, String listId, CreateItemRequest request) {
        if (!permissionService.hasAtLeast(listId, userId, Role.WRITE)) {
            throw new NotFoundException("Not found");
        }
        String name = request.name == null ? null : request.name.trim();
        if (name == null || name.isEmpty()) throw new BadRequestException("Item name is required");

        BigDecimal normalizedPrice = normalizePrice(request.price);

        Item item = new Item();
        if (request.id != null && !request.id.isBlank()) item.setId(request.id);
        item.setListId(listId);
        item.setName(name.length() > 40 ? name.substring(0, 40) : name);
        item.setCategory(request.category == null || request.category.isBlank() ? "Other" : request.category);
        item.setUnit(request.unit == null || request.unit.isBlank() ? "packet" : request.unit);
        item.setPrice(normalizedPrice);
        item.setUpdatedBy(userId);

        try {
            item = itemRepository.save(item);
        } catch (DataIntegrityViolationException e) {
            if (request.id != null && !request.id.isBlank()) {
                Optional<Item> existing = itemRepository.findById(request.id);
                if (existing.isPresent()) return new ItemDto(existing.get());
            }
            throw e;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listId", listId);
        payload.put("item", new ItemDto(item));
        realtimeService.emitToList(listId, "item:created", payload);

        return new ItemDto(item);
    }

    @Transactional
    public ItemDto updateItem(String userId, String listId, String itemId, Map<String, Object> updates) {
        if (!permissionService.hasAtLeast(listId, userId, Role.WRITE)) {
            throw new NotFoundException("Not found");
        }
        Optional<Item> existingOpt = itemRepository.findById(itemId);
        // P2025-equivalent: item (or its list) was deleted on another device
        // while this update was queued offline. Nothing to apply; treat as
        // already-settled rather than an error that would sit in the
        // client's retry queue forever -- signalled to the controller as
        // "no content" (204), same as the original.
        if (existingOpt.isEmpty()) return null;

        Item item = existingOpt.get();
        applyAllowedUpdates(item, updates);
        item.setUpdatedBy(userId);
        item = itemRepository.save(item);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listId", listId);
        payload.put("item", new ItemDto(item));
        realtimeService.emitToList(listId, "item:updated", payload);

        return new ItemDto(item);
    }

    @Transactional
    public void deleteItem(String userId, String listId, String itemId) {
        if (!permissionService.hasAtLeast(listId, userId, Role.WRITE)) {
            throw new NotFoundException("Not found");
        }
        itemRepository.findById(itemId).ifPresent(itemRepository::delete); // already gone is fine -- that's the goal state
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listId", listId);
        payload.put("itemId", itemId);
        realtimeService.emitToList(listId, "item:deleted", payload);
    }

    // ---- helpers ----

    private void applyAllowedUpdates(Item item, Map<String, Object> updates) {
        for (String key : ALLOWED_UPDATE_FIELDS) {
            if (!updates.containsKey(key)) continue; // mirrors `req.body[k] !== undefined`
            Object value = updates.get(key);
            switch (key) {
                case "name" -> item.setName(value == null ? null : value.toString());
                case "category" -> item.setCategory(value == null ? null : value.toString());
                case "unit" -> item.setUnit(value == null ? null : value.toString());
                case "note" -> item.setNote(value == null ? null : value.toString());
                case "qty" -> item.setQty(value == null ? null : toInt(value));
                case "checked" -> item.setChecked(value == null ? null : toBool(value));
                case "skipped" -> item.setSkipped(value == null ? null : toBool(value));
                case "price" -> {
                    // Empty string means "don't update the existing price".
                    if ("".equals(value)) continue;
                    item.setPrice(normalizePrice(value));
                }
                default -> { /* not reachable */ }
            }
        }
    }

    private BigDecimal normalizePrice(Object price) {
        if (price == null) return null;
        if (price instanceof String s) {
            if (s.isBlank()) return null;
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid price");
            }
        }
        if (price instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw new BadRequestException("Invalid price");
            return BigDecimal.valueOf(d);
        }
        throw new BadRequestException("Invalid price");
    }

    private Integer toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid qty");
        }
    }

    private Boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
