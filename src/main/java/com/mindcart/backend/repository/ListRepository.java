package com.mindcart.backend.repository;

import com.mindcart.backend.entity.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ListRepository extends JpaRepository<ShoppingList, String> {
    List<ShoppingList> findByOwnerId(String ownerId);
}
