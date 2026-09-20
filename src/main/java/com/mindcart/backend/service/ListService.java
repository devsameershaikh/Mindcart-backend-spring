package com.mindcart.backend.service;

import com.mindcart.backend.dto.*;
import com.mindcart.backend.entity.*;
import com.mindcart.backend.exception.BadRequestException;
import com.mindcart.backend.exception.NotFoundException;
import com.mindcart.backend.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ListService {

    private final ListRepository listRepository;
    private final ListMemberRepository listMemberRepository;
    private final ItemRepository itemRepository;
    private final InviteRepository inviteRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final RealtimeService realtimeService;

    public ListService(ListRepository listRepository, ListMemberRepository listMemberRepository,
                        ItemRepository itemRepository, InviteRepository inviteRepository,
                        UserRepository userRepository, PermissionService permissionService,
                        RealtimeService realtimeService) {
        this.listRepository = listRepository;
        this.listMemberRepository = listMemberRepository;
        this.itemRepository = itemRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.realtimeService = realtimeService;
    }

    // GET /lists -> every list the user owns or has been shared into, with role
    @Transactional(readOnly = true)
    public List<ListDto> getListsForUser(String userId) {
        List<ListMember> memberships = listMemberRepository.findByUserId(userId);
        List<ListDto> result = new ArrayList<>();
        for (ListMember membership : memberships) {
            ShoppingList list = listRepository.findById(membership.getListId()).orElse(null);
            if (list == null) continue; // defensive: FK cascade should prevent this
            result.add(toListDto(list, membership.getRole()));
        }
        return result;
    }

    // POST /lists { id?, name } -> creates a list, caller becomes OWNER.
    //
    // `id` is optional and comes from offline-first clients that generate a
    // permanent id up front so a list created while offline keeps the same
    // id once it finally syncs. A retry of this exact request landing twice
    // (unique-constraint hit on that id) is treated as an idempotent success.
    @Transactional
    public ListDto createList(String userId, CreateListRequest request) {
        String name = request.name == null ? "" : request.name.trim();
        if (name.isEmpty()) throw new BadRequestException("List name is required");

        ShoppingList list = new ShoppingList();
        if (request.id != null && !request.id.isBlank()) list.setId(request.id);
        list.setName(name);
        list.setOwnerId(userId);

        try {
            list = listRepository.save(list);
        } catch (DataIntegrityViolationException e) {
            if (request.id != null && !request.id.isBlank()) {
                Optional<ShoppingList> existing = listRepository.findById(request.id);
                if (existing.isPresent()) {
                    return toListDto(existing.get(), Role.OWNER);
                }
            }
            throw e;
        }

        ListMember owner = new ListMember();
        owner.setListId(list.getId());
        owner.setUserId(userId);
        owner.setRole(Role.OWNER);
        listMemberRepository.save(owner);

        grantToStandingFamilyMembers(userId, list);

        return toListDto(list, Role.OWNER);
    }

    // A "family member" (someone with an ACCEPTED allLists invite from this
    // owner) is meant to see every list the owner has -- including ones
    // created AFTER they accepted. Grants access automatically right after
    // a new list is created and notifies live, with no separate
    // invite/accept step needed for lists that come later.
    private void grantToStandingFamilyMembers(String ownerId, ShoppingList list) {
        List<Invite> standingInvites = inviteRepository
                .findBySenderIdAndInviteAllListsAndStatusAndRecipientIdIsNotNull(ownerId, true, InviteStatus.ACCEPTED);
        if (standingInvites.isEmpty()) return;

        // Dedupe by recipient -- old data may have two ACCEPTED allLists
        // invites for the same person from before the duplicate-invite guard existed.
        Map<String, Invite> byRecipient = new LinkedHashMap<>();
        for (Invite inv : standingInvites) {
            byRecipient.putIfAbsent(inv.getRecipientId(), inv);
        }

        for (Map.Entry<String, Invite> entry : byRecipient.entrySet()) {
            String recipientId = entry.getKey();
            Invite inv = entry.getValue();
            ListMember member = listMemberRepository.findByListIdAndUserId(list.getId(), recipientId)
                    .orElseGet(() -> {
                        ListMember m = new ListMember();
                        m.setListId(list.getId());
                        m.setUserId(recipientId);
                        return m;
                    });
            member.setRole(inv.getRole());
            listMemberRepository.save(member);
        }

        List<ListMember> fullMembers = listMemberRepository.findByListId(list.getId());
        List<MemberDto> membersPayload = fullMembers.stream()
                .map(m -> userRepository.findById(m.getUserId())
                        .map(u -> new MemberDto(u, m.getRole()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (Map.Entry<String, Invite> entry : byRecipient.entrySet()) {
            String recipientId = entry.getKey();
            Invite inv = entry.getValue();
            Map<String, Object> payload = new LinkedHashMap<>();
            Map<String, Object> listPayload = new LinkedHashMap<>();
            listPayload.put("id", list.getId());
            listPayload.put("name", list.getName() + " - Shared");
            listPayload.put("ownerId", list.getOwnerId());
            listPayload.put("createdAt", list.getCreatedAt());
            listPayload.put("role", inv.getRole());
            listPayload.put("items", List.of());
            listPayload.put("members", membersPayload);
            payload.put("list", listPayload);
            realtimeService.emitToUser(recipientId, "list:granted", payload);
        }
    }

    @Transactional
    public ListDto updateList(String userId, String listId, UpdateListRequest request) {
        if (!permissionService.hasAtLeast(listId, userId, Role.WRITE)) {
            throw new NotFoundException("Not found");
        }
        String name = request.name == null ? "" : request.name.trim();
        if (name.isEmpty()) throw new BadRequestException("List name is required");

        Optional<ShoppingList> existing = listRepository.findById(listId);
        if (existing.isEmpty()) return null; // caller returns 204 -- list already gone

        ShoppingList list = existing.get();
        list.setName(name);
        list = listRepository.save(list);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listId", listId);
        payload.put("name", name);
        realtimeService.emitToList(listId, "list:updated", payload);

        Role callerRole = permissionService.getMembership(listId, userId).map(ListMember::getRole).orElse(null);
        return toListDto(list, callerRole);
    }

    @Transactional
    public void deleteList(String userId, String listId) {
        if (!permissionService.hasAtLeast(listId, userId, Role.OWNER)) {
            throw new NotFoundException("Not found");
        }
        listRepository.findById(listId).ifPresent(listRepository::delete); // cascades to members/items/invites
        realtimeService.emitToList(listId, "list:deleted", Map.of("listId", listId));
    }

    // ---- Assembly helpers ----

    ListDto toListDto(ShoppingList list, Role callerRole) {
        List<ItemDto> items = itemRepository.findByListId(list.getId()).stream()
                .map(ItemDto::new)
                .collect(Collectors.toList());
        List<MemberDto> members = listMemberRepository.findByListId(list.getId()).stream()
                .map(m -> userRepository.findById(m.getUserId()).map(u -> new MemberDto(u, m.getRole())).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new ListDto(list, callerRole, items, members);
    }
}
