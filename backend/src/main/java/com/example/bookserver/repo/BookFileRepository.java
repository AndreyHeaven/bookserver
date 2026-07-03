package com.example.bookserver.repo;

import com.example.bookserver.domain.BookFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookFileRepository extends JpaRepository<BookFile, Long> {

    Optional<BookFile> findByStoragePath(String storagePath);

    Optional<BookFile> findByStoragePathAndEntryName(String storagePath, String entryName);
}
