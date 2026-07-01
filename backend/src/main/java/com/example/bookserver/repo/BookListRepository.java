package com.example.bookserver.repo;

import com.example.bookserver.domain.BookList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookListRepository extends JpaRepository<BookList, Long> {

    List<BookList> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Page<BookList> findByOwnerId(Long ownerId, Pageable pageable);
}
