package com.mindcart.backend.service;

import com.mindcart.backend.dto.AuthResponse;
import com.mindcart.backend.dto.UserPublicDto;
import com.mindcart.backend.entity.*;
import com.mindcart.backend.exception.NotFoundException;
import com.mindcart.backend.repository.InviteRepository;
import com.mindcart.backend.repository.ItemRepository;
import com.mindcart.backend.repository.ListMemberRepository;
import com.mindcart.backend.repository.ListRepository;
import com.mindcart.backend.repository.UserRepository;
import com.mindcart.backend.security.GoogleTokenVerifier;
import com.mindcart.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Seeded into every brand-new user's first "Groceries" list at signup.
    // Keep in sync with the mobile app's own local DEFAULT_ITEMS placeholder,
    // same as the original Node comment noted.
    private static final List<String[]> DEFAULT_ITEMS = List.of(
            new String[]{"Milk", "Dairy", "liter"},
            new String[]{"Rice", "Grains & Pulses", "kg"},
            new String[]{"Sugar", "Kitchen", "kg"},
            new String[]{"Cooking Oil", "Oil & Ghee", "liter"},
            new String[]{"Wheat Flour (Atta)", "Grains & Pulses", "kg"},
            new String[]{"Salt", "Spices & Masala", "kg"},
            new String[]{"Tea", "Beverages", "packet"},
            new String[]{"Onion", "Vegetables", "kg"}
    );

    private final UserRepository userRepository;
    private final ListRepository listRepository;
    private final ListMemberRepository listMemberRepository;
    private final ItemRepository itemRepository;
    private final InviteRepository inviteRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, ListRepository listRepository,
                        ListMemberRepository listMemberRepository, ItemRepository itemRepository,
                        InviteRepository inviteRepository, GoogleTokenVerifier googleTokenVerifier,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.listRepository = listRepository;
        this.listMemberRepository = listMemberRepository;
        this.itemRepository = itemRepository;
        this.inviteRepository = inviteRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse signInWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(idToken);

        // Upsert can't tell us whether it created or updated a row, and we
        // only ever want to seed the default list once, exactly on first
        // signup -- so look the user up explicitly first, same as the
        // original implementation.
        var existing = userRepository.findByGoogleId(profile.googleId);
        boolean isNewUser = existing.isEmpty();

        User user;
        if (existing.isPresent()) {
            user = existing.get();
            user.setName(profile.name);
            user.setAvatarUrl(profile.avatarUrl);
            user.setEmail(profile.email);
            user = userRepository.save(user);
        } else {
            user = new User();
            user.setGoogleId(profile.googleId);
            user.setEmail(profile.email);
            user.setName(profile.name);
            user.setAvatarUrl(profile.avatarUrl);
            user = userRepository.save(user);
        }

        if (isNewUser) {
            try {
                seedDefaultGroceriesList(user.getId());
            } catch (Exception e) {
                // Don't fail the whole sign-in over this -- worst case a
                // brand-new user just lands on an empty state.
                log.error("Couldn't seed default Groceries list for new user: {}", user.getId(), e);
            }
        }

        // Auto-attach any pending invites that were sent to this email
        // before they ever signed up (recipientId is null until first login).
//        List<Invite> pending = inviteRepository.findByRecipientEmailAndRecipientIdIsNullAndStatus(
//                user.getEmail(), InviteStatus.PENDING);
//        if (!pending.isEmpty()) {
//            for (Invite invite : pending) {
//                invite.setRecipientId(user.getId());
//            }
//            inviteRepository.saveAll(pending);
//        }

        String token = jwtService.signSession(user);
        return new AuthResponse(token, new UserPublicDto(user));
    }

    private void seedDefaultGroceriesList(String userId) {
        ShoppingList list = new ShoppingList();
        list.setId(UUID.randomUUID().toString());
        list.setName("Groceries");
        list.setOwnerId(userId);
        list = listRepository.save(list);

        ListMember owner = new ListMember();
        owner.setListId(list.getId());
        owner.setUserId(userId);
        owner.setRole(Role.OWNER);
        listMemberRepository.save(owner);

        for (String[] def : DEFAULT_ITEMS) {
            Item item = new Item();
            item.setListId(list.getId());
            item.setName(def[0]);
            item.setCategory(def[1]);
            item.setUnit(def[2]);
            item.setUpdatedBy(userId);
            itemRepository.save(item);
        }
    }

    public UserPublicDto me(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new UserPublicDto(user);
    }
}
