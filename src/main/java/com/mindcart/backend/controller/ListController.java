package com.mindcart.backend.controller;

import com.mindcart.backend.dto.CreateItemRequest;
import com.mindcart.backend.dto.CreateListRequest;
import com.mindcart.backend.dto.ItemDto;
import com.mindcart.backend.dto.ListDto;
import com.mindcart.backend.dto.UpdateListRequest;
import com.mindcart.backend.service.ItemService;
import com.mindcart.backend.service.ListService;
import com.mindcart.backend.util.AuthUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/lists")
public class ListController {

    private final ListService listService;
    private final ItemService itemService;

    public ListController(ListService listService, ItemService itemService) {
        this.listService = listService;
        this.itemService = itemService;
    }

    @GetMapping
    public Map<String, List<ListDto>> getLists() {
        String userId = AuthUtil.currentUser().getUserId();
        return Map.of("lists", listService.getListsForUser(userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, ListDto>> createList(@RequestBody CreateListRequest request) {
        String userId = AuthUtil.currentUser().getUserId();
        ListDto list = listService.createList(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("list", list));
    }

    @PatchMapping("/{listId}")
    public ResponseEntity<Map<String, ListDto>> updateList(@PathVariable String listId,
                                                             @RequestBody UpdateListRequest request) {
        String userId = AuthUtil.currentUser().getUserId();
        ListDto list = listService.updateList(userId, listId, request);
        if (list == null) return ResponseEntity.noContent().build(); // list already gone -- nothing to rename
        return ResponseEntity.ok(Map.of("list", list));
    }

    @DeleteMapping("/{listId}")
    public ResponseEntity<Void> deleteList(@PathVariable String listId) {
        String userId = AuthUtil.currentUser().getUserId();
        listService.deleteList(userId, listId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Items ----------

    @PostMapping("/{listId}/items")
    public ResponseEntity<Map<String, ItemDto>> createItem(@PathVariable String listId,
                                                             @RequestBody CreateItemRequest request) {
        String userId = AuthUtil.currentUser().getUserId();
        ItemDto item = itemService.createItem(userId, listId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("item", item));
    }

    @PatchMapping("/{listId}/items/{itemId}")
    public ResponseEntity<Map<String, ItemDto>> updateItem(@PathVariable String listId,
                                                             @PathVariable String itemId,
                                                             @RequestBody Map<String, Object> updates) {
        String userId = AuthUtil.currentUser().getUserId();
        ItemDto item = itemService.updateItem(userId, listId, itemId, updates);
        if (item == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(Map.of("item", item));
    }

    @DeleteMapping("/{listId}/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String listId, @PathVariable String itemId) {
        String userId = AuthUtil.currentUser().getUserId();
        itemService.deleteItem(userId, listId, itemId);
        return ResponseEntity.noContent().build();
    }
}
