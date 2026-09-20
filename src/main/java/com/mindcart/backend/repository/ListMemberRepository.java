package com.mindcart.backend.repository;

import com.mindcart.backend.entity.ListMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ListMemberRepository extends JpaRepository<ListMember, String> {
    Optional<ListMember> findByListIdAndUserId(String listId, String userId);
    List<ListMember> findByUserId(String userId);
    List<ListMember> findByListId(String listId);
    void deleteByListIdAndUserId(String listId, String userId);
}
