package com.mindcart.backend.service;

import com.mindcart.backend.dto.InviteDto;
import com.mindcart.backend.dto.InviteRequest;
import com.mindcart.backend.dto.UserPublicDto;
import com.mindcart.backend.entity.Invite;
import com.mindcart.backend.entity.InviteStatus;
import com.mindcart.backend.entity.ListMember;
import com.mindcart.backend.entity.Role;
import com.mindcart.backend.entity.ShoppingList;
import com.mindcart.backend.entity.User;
import com.mindcart.backend.exception.BadRequestException;
import com.mindcart.backend.exception.ConflictException;
import com.mindcart.backend.exception.NotFoundException;
import com.mindcart.backend.repository.InviteRepository;
import com.mindcart.backend.repository.ListMemberRepository;
import com.mindcart.backend.repository.ListRepository;
import com.mindcart.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharingService {

    private static final String TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final InviteRepository inviteRepository;
    private final ListRepository listRepository;
    private final ListMemberRepository listMemberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final RealtimeService realtimeService;
    private final PushNotificationService pushService;


    // POST /sharing/invites
    // { recipientEmail, role, listId }         -> invite to ONE list
    // { recipientEmail, role, allLists: true }  -> "family member" share of every list the sender owns
    @Transactional
    public InviteDto createInvite(String senderId, String senderEmail, InviteRequest request) {
        log.info("senderId :{} : senderEmail :{}  ",senderId,senderEmail);
        String email = request.recipientEmail == null ? "" : request.recipientEmail.trim().toLowerCase();
        if (email.isEmpty()) throw new BadRequestException("recipientEmail is required");

        Role role;
        try {
            role = Role.valueOf(request.role == null ? "" : request.role);
            if (role != Role.READ && role != Role.WRITE) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("role must be READ or WRITE");
        }

        if (email.equals(senderEmail.toLowerCase())) {
            throw new BadRequestException("You can't invite yourself");
        }

        boolean allLists = Boolean.TRUE.equals(request.allLists);
        if (!allLists) {
            if (request.listId == null || request.listId.isBlank()) {
                throw new BadRequestException("listId is required unless allLists is true");
            }
            if (!permissionService.hasAtLeast(request.listId, senderId, Role.OWNER)) {
                throw new NotFoundException("Not found");
            }
        }

        Optional<User> recipientOpt = userRepository.findByEmail(email);
        if (recipientOpt.isEmpty()) {
            throw new NotFoundException("No MindCart account found with this email address");
        }
        User recipient = recipientOpt.get();

        // Don't create a second PENDING invite for the same email + same
        // scope -- re-inviting (e.g. after a typo, or forgetting one was
        // already sent) would otherwise leave the recipient with two cards
        // for the same thing.
        Optional<Invite> duplicate = allLists
                ? inviteRepository.findFirstBySenderIdAndRecipientEmailAndStatusAndInviteAllLists(
                        senderId, email, InviteStatus.PENDING, true)
                : inviteRepository.findFirstBySenderIdAndRecipientEmailAndStatusAndListId(
                        senderId, email, InviteStatus.PENDING, request.listId);
        if (duplicate.isPresent()) {
            throw new ConflictException("There's already a pending invite for this person");
        }

        Invite invite = new Invite();
        invite.setListId(allLists ? null : request.listId);
        invite.setInviteAllLists(allLists);
        invite.setSenderId(senderId);
        invite.setRecipientEmail(email);
        invite.setRecipientId(recipient.getId());
        invite.setRole(role);
        invite.setToken(generateToken(24));
        invite = inviteRepository.save(invite);

        InviteDto dto = InviteDto.from(invite);
        dto.sender = null; // sender omitted on the create response, same as the original shape

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invite", dto);
        realtimeService.emitToUser(recipient.getId(), "invite:received", payload);

        // Push covers the case the socket can't: recipient's app is
        // backgrounded or killed. The socket handler in App.js already
        // updates the UI when they *are* open, so a user who's looking at
        // the app gets both -- which is fine, they're different surfaces.
        String senderName = userRepository.findById(senderId)
                .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                .orElse("Someone");
        String listName = allLists
                ? null
                : listRepository.findById(request.listId).map(ShoppingList::getName).orElse(null);
        String body = allLists
                ? senderName + " wants to share all their lists with you"
                : (listName != null
                ? senderName + " invited you to \"" + listName + "\""
                : senderName + " invited you to a shopping list");

        log.info("noti body :{}",body);

        pushService.sendToUser(recipient.getId(), PushNotificationService.PushMessage.builder()
                .title("New MindCart invite")
                .body(body)
                .type("invite:received")
                .channel("invites")
                .data("inviteId", invite.getId())
                .data("listId", invite.getListId())
                .data("allLists", allLists)
                .build());



        return dto;

    }

    // GET /sharing/invites -> invites sent BY me and invites addressed TO me
    @Transactional(readOnly = true)
    public Map<String, List<InviteDto>> getInvites(String userId) {
        User me = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        List<InviteDto> sent = inviteRepository.findBySenderIdOrderByCreatedAtDesc(userId).stream()
                .map(InviteDto::from)
                .collect(Collectors.toList());

        List<Invite> receivedById = inviteRepository
                .findByRecipientIdAndStatusOrderByCreatedAtDesc(userId, InviteStatus.PENDING);
        List<Invite> receivedByEmail = inviteRepository
                .findByRecipientEmailAndStatusOrderByCreatedAtDesc(me.getEmail(), InviteStatus.PENDING);

        List<Invite> merged = new ArrayList<>(receivedById);
        for (Invite inv : receivedByEmail) {
            if (merged.stream().noneMatch(existing -> existing.getId().equals(inv.getId()))) {
                merged.add(inv);
            }
        }
        List<InviteDto> received = merged.stream()
                .map(inv -> {
                    InviteDto dto = InviteDto.from(inv);

                    ShoppingList list = listRepository
                            .findById(dto.listId)
                            .orElse(null);

                    if (list != null) {
                        dto.setListName(list.getName());
                    }

                    userRepository.findById(inv.getSenderId()).ifPresent(sender -> {
                        dto.sender = new UserPublicDto(sender);
                    });

                    return dto;
                })
                .toList();

        Map<String, List<InviteDto>> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("received", received);
        return result;
    }

    // POST /sharing/invites/:id/accept
    @Transactional
    public List<String> acceptInvite(String userId, String userEmail, String inviteId) {
        Invite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getStatus() != InviteStatus.PENDING) {
            throw new NotFoundException("Invite not found");
        }
        requireIntendedRecipient(invite, userId, userEmail);

        List<String> targetListIds = invite.getInviteAllLists()
                ? listRepository.findByOwnerId(invite.getSenderId()).stream().map(ShoppingList::getId).collect(Collectors.toList())
                : List.of(invite.getListId());

        for (String listId : targetListIds) {
            ListMember member = listMemberRepository.findByListIdAndUserId(listId, userId)
                    .orElseGet(() -> {
                        ListMember m = new ListMember();
                        m.setListId(listId);
                        m.setUserId(userId);
                        return m;
                    });
            member.setRole(invite.getRole());
            listMemberRepository.save(member);
        }

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setRecipientId(userId);
        invite.setRespondedAt(Instant.now());
        inviteRepository.save(invite);

        for (String listId : targetListIds) {
            realtimeService.emitToList(listId, "list:memberJoined", Map.of("listId", listId, "userId", userId));
        }

        realtimeService.emitToUser(invite.getSenderId(), "invite:accepted", Map.of("inviteId", invite.getId()));

        String accepterName = userRepository.findById(userId)
                .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                .orElse("Someone");
        pushService.sendToUser(invite.getSenderId(), PushNotificationService.PushMessage.builder()
                .title("Invite accepted")
                .body(accepterName + " joined your list")
                .type("invite:accepted")
                .channel("invites")
                .data("inviteId", invite.getId())
                .build());

        return targetListIds;
    }

    @Transactional
    public void declineInvite(String userId, String userEmail, String inviteId) {
        Invite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getStatus() != InviteStatus.PENDING) {
            throw new NotFoundException("Invite not found");
        }
        requireIntendedRecipient(invite, userId, userEmail);

        invite.setStatus(InviteStatus.DECLINED);
        invite.setRespondedAt(Instant.now());
        invite = inviteRepository.save(invite);

        realtimeService.emitToUser(invite.getSenderId(), "invite:declined", Map.of("inviteId", invite.getId()));
    }

    @Transactional
    public void revokeInvite(String userId, String inviteId) {
        Invite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || !invite.getSenderId().equals(userId)) {
            throw new NotFoundException("Not found");
        }
        inviteRepository.delete(invite);

        // Previously this never told the recipient -- their pending-invite
        // card just sat there until they tried to act on it and got a stale
        // "not found". Push it live if they have an account.
        if (invite.getRecipientId() != null) {
            realtimeService.emitToUser(invite.getRecipientId(), "invite:revoked", Map.of("inviteId", invite.getId()));
        }
    }

    // PATCH /sharing/lists/:listId/members/:userId { role } -> owner changes someone's permission
    @Transactional
    public ListMember updateMemberRole(String callerId, String listId, String targetUserId, String roleStr) {
        if (!permissionService.hasAtLeast(listId, callerId, Role.OWNER)) {
            throw new NotFoundException("Not found");
        }
        Role role;
        if (!"READ".equals(roleStr) && !"WRITE".equals(roleStr)) {
            throw new BadRequestException("Invalid role");
        }
        role = Role.valueOf(roleStr);

        // Nothing in the UI lets an owner target their own row, but the API
        // must still refuse it directly: demoting the owner's own
        // ListMember role would desync it from List.ownerId (every
        // permission check reads the ListMember role, not ownerId), with no
        // recovery path -- hasAtLeast(...,"OWNER") would then fail for the
        // actual owner on every future request against this list,
        // permanently locking it.
        ShoppingList list = listRepository.findById(listId).orElse(null);
        if (list != null && list.getOwnerId().equals(targetUserId)) {
            throw new BadRequestException("Can't change the list owner's role");
        }

        ListMember member = listMemberRepository.findByListIdAndUserId(listId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Not found"));
        member.setRole(role);
        member = listMemberRepository.save(member);

        realtimeService.emitToList(listId, "list:memberRoleChanged",
                Map.of("listId", listId, "userId", targetUserId, "role", role));
        return member;
    }

    // DELETE /sharing/lists/:listId/members/:userId -> owner removes access
    @Transactional
    public void removeMember(String callerId, String listId, String targetUserId) {
        if (!permissionService.hasAtLeast(listId, callerId, Role.OWNER)) {
            throw new NotFoundException("Not found");
        }
        ShoppingList list = listRepository.findById(listId).orElse(null);
        if (list != null && list.getOwnerId().equals(targetUserId)) {
            throw new BadRequestException("Can't remove the list owner");
        }
        listMemberRepository.deleteByListIdAndUserId(listId, targetUserId);
        realtimeService.emitToList(listId, "list:memberRemoved", Map.of("listId", listId, "userId", targetUserId));
    }

    // ---- helpers ----

    // MUST verify the caller is actually who this invite was sent to --
    // without this, any signed-in user who knows/guesses an invite id could
    // accept/decline someone else's invite. 404 (not 403) on a mismatch,
    // same reasoning as PermissionService: don't reveal that an invite with
    // this id exists to someone it isn't for.
    private void requireIntendedRecipient(Invite invite, String userId, String userEmail) {
        boolean isIntendedRecipient = invite.getRecipientId() != null
                ? invite.getRecipientId().equals(userId)
                : invite.getRecipientEmail().equalsIgnoreCase(userEmail == null ? "" : userEmail);
        if (!isIntendedRecipient) throw new NotFoundException("Invite not found");
    }

    private String generateToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TOKEN_ALPHABET.charAt(SECURE_RANDOM.nextInt(TOKEN_ALPHABET.length())));
        }
        return sb.toString();
    }
}
