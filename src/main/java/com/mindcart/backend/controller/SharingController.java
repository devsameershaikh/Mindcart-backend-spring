package com.mindcart.backend.controller;

import com.mindcart.backend.dto.InviteDto;
import com.mindcart.backend.dto.InviteRequest;
import com.mindcart.backend.dto.UpdateMemberRoleRequest;
import com.mindcart.backend.exception.TooManyRequestsException;
import com.mindcart.backend.security.AuthenticatedUser;
import com.mindcart.backend.security.RateLimiter;
import com.mindcart.backend.service.SharingService;
import com.mindcart.backend.util.AuthUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/sharing")
@Slf4j
public class SharingController {

    private final SharingService sharingService;
    private final RateLimiter rateLimiter;

    @Value("${app.rate-limit.invites.capacity:20}")
    private int inviteCapacity;
    @Value("${app.rate-limit.invites.refill-per-minute:20}")
    private int inviteRefillPerMinute;

    public SharingController(SharingService sharingService, RateLimiter rateLimiter) {
        this.sharingService = sharingService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/invites")
    public ResponseEntity<Map<String, InviteDto>> createInvite(@RequestBody InviteRequest request,
                                                                 HttpServletRequest httpRequest) {
        AuthenticatedUser me = AuthUtil.currentUser();
        // Invite creation reveals whether an email has a MindCart account
        // (404 vs success) -- rate-limit it so it can't be used to
        // enumerate registered users at scale.
        log.info("request : {}",request.toString());
        if (!rateLimiter.tryConsume("invite:" + me.getUserId(), inviteCapacity, inviteRefillPerMinute)) {
            throw new TooManyRequestsException("Too many invites sent. Please try again shortly.");
        }
        InviteDto invite = sharingService.createInvite(me.getUserId(), me.getEmail(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("invite", invite));
    }

    @GetMapping("/invites")
    public Map<String, List<InviteDto>> getInvites() {
        log.info(" fetching invites");
        AuthenticatedUser me = AuthUtil.currentUser();
        return sharingService.getInvites(me.getUserId());
    }

    @PostMapping("/invites/{id}/accept")
    public Map<String, Object> acceptInvite(@PathVariable String id) {
        AuthenticatedUser me = AuthUtil.currentUser();
        List<String> listIds = sharingService.acceptInvite(me.getUserId(), me.getEmail(), id);
        return Map.of("ok", true, "listIds", listIds);
    }

    @PostMapping("/invites/{id}/decline")
    public Map<String, Object> declineInvite(@PathVariable String id) {
        AuthenticatedUser me = AuthUtil.currentUser();
        sharingService.declineInvite(me.getUserId(), me.getEmail(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/invites/{id}/revoke")
    public Map<String, Object> revokeInvite(@PathVariable String id) {
        AuthenticatedUser me = AuthUtil.currentUser();
        sharingService.revokeInvite(me.getUserId(), id);
        return Map.of("ok", true);
    }

    @PatchMapping("/lists/{listId}/members/{userId}")
    public Map<String, Object> updateMemberRole(@PathVariable String listId, @PathVariable String userId,
                                                 @RequestBody UpdateMemberRoleRequest request) {
        AuthenticatedUser me = AuthUtil.currentUser();
        var member = sharingService.updateMemberRole(me.getUserId(), listId, userId, request.role);
        return Map.of("member", member);
    }

    @DeleteMapping("/lists/{listId}/members/{userId}")
    public Map<String, Object> removeMember(@PathVariable String listId, @PathVariable String userId) {
        AuthenticatedUser me = AuthUtil.currentUser();
        sharingService.removeMember(me.getUserId(), listId, userId);
        return Map.of("ok", true);
    }
}
