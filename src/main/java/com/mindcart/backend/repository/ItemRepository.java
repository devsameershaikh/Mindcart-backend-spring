package com.mindcart.backend.repository;

import com.mindcart.backend.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findByListId(String listId);
}
