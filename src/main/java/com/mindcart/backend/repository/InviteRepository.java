package com.mindcart.backend.repository;

import com.mindcart.backend.entity.Invite;
import com.mindcart.backend.entity.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, String> {
    List<Invite> findBySenderIdOrderByCreatedAtDesc(String senderId);

    List<Invite> findByRecipientIdAndStatusOrderByCreatedAtDesc(String recipientId, InviteStatus status);

    List<Invite> findByRecipientEmailAndStatusOrderByCreatedAtDesc(String recipientEmail, InviteStatus status);

    Optional<Invite> findFirstBySenderIdAndRecipientEmailAndStatusAndInviteAllLists(
            String senderId, String recipientEmail, InviteStatus status, Boolean inviteAllLists);

    Optional<Invite> findFirstBySenderIdAndRecipientEmailAndStatusAndListId(
            String senderId, String recipientEmail, InviteStatus status, String listId);

    List<Invite> findBySenderIdAndInviteAllListsAndStatusAndRecipientIdIsNotNull(
            String senderId, Boolean inviteAllLists, InviteStatus status);

    List<Invite> findByRecipientEmailAndRecipientIdIsNullAndStatus(String recipientEmail, InviteStatus status);
}
