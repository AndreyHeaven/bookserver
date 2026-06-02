package com.example.bookserver.repo;

import com.example.bookserver.domain.BookListItem;
import com.example.bookserver.domain.BookListItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookListItemRepository extends JpaRepository<BookListItem, BookListItemId> {
}
