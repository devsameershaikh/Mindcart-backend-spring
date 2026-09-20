package com.mindcart.backend.service;

import com.mindcart.backend.entity.ListMember;
import com.mindcart.backend.entity.Role;
import com.mindcart.backend.repository.ListMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionService {

    private final ListMemberRepository listMemberRepository;

    public PermissionService(ListMemberRepository listMemberRepository) {
        this.listMemberRepository = listMemberRepository;
    }

    /** Returns the caller's ListMember row for a list, or empty if they have no access. */
    public Optional<ListMember> getMembership(String listId, String userId) {
        return listMemberRepository.findByListIdAndUserId(listId, userId);
    }

    /**
     * Throws-free helper: resolves to true/false instead of throwing, so
     * callers can decide whether to 404 (never 403 -- see NotFoundException
     * javadoc for why we deliberately don't distinguish "no access" from
     * "doesn't exist" in responses).
     */
    public boolean hasAtLeast(String listId, String userId, Role minRole) {
        return getMembership(listId, userId)
                .map(m -> m.getRole().atLeast(minRole))
                .orElse(false);
    }
}
