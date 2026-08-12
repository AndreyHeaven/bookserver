package com.example.bookserver.repo;

import com.example.bookserver.domain.BookViewHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface BookViewHistoryRepository extends JpaRepository<BookViewHistory, Long> {

    Optional<BookViewHistory> findByUserIdAndBookId(Long userId, Long bookId);

    Page<BookViewHistory> findByUserIdAndViewedAtAfter(Long userId, OffsetDateTime after, Pageable pageable);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("delete from BookViewHistory h where h.viewedAt <= :cutoff")
    void deleteExpired(@Param("cutoff") OffsetDateTime cutoff);

    @Modifying
    @Query(value = """
            delete from book_view_history
            where id in (
                select id from (
                    select id, row_number() over (partition by user_id order by viewed_at desc, id desc) as row_number
                    from book_view_history
                ) ranked
                where ranked.row_number > :maxEntries
            )
            """, nativeQuery = true)
    void trimToMaxEntriesPerUser(@Param("maxEntries") int maxEntries);
}
