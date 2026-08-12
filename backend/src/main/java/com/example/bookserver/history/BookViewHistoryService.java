package com.example.bookserver.history;

import com.example.bookserver.domain.BookViewHistory;
import com.example.bookserver.domain.SiteSettings;
import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.repo.BookViewHistoryRepository;
import com.example.bookserver.repo.SiteSettingsRepository;
import com.example.bookserver.repo.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class BookViewHistoryService {

    private final BookViewHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final SiteSettingsRepository settingsRepository;

    public BookViewHistoryService(BookViewHistoryRepository historyRepository,
                                  UserRepository userRepository,
                                  BookRepository bookRepository,
                                  SiteSettingsRepository settingsRepository) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public void record(Long userId, Long bookId) {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseThrow();
        OffsetDateTime viewedAt = OffsetDateTime.now();
        BookViewHistory history = historyRepository.findByUserIdAndBookId(userId, bookId).orElseGet(() -> {
            BookViewHistory entry = new BookViewHistory();
            entry.setUser(userRepository.getReferenceById(userId));
            entry.setBook(bookRepository.getReferenceById(bookId));
            return entry;
        });
        history.setViewedAt(viewedAt);
        historyRepository.save(history);
        applyLimits(settings, viewedAt);
    }

    @Transactional(readOnly = true)
    public Page<HistoryDto> list(Long userId, Pageable pageable) {
        SiteSettings settings = settingsRepository.findById(SiteSettings.SINGLETON_ID).orElseThrow();
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(settings.getHistoryRetentionDays());
        return historyRepository.findByUserIdAndViewedAtAfter(userId, cutoff, pageable).map(HistoryDto::from);
    }

    @Transactional(readOnly = true)
    public void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
    }

    @Transactional
    public void delete(Long userId, Long id) {
        historyRepository.findById(id)
                .filter(history -> history.getUser().getId().equals(userId))
                .ifPresent(historyRepository::delete);
    }

    @Transactional
    public void clear(Long userId) {
        historyRepository.deleteByUserId(userId);
    }

    @Transactional
    public void applyLimits(SiteSettings settings) {
        applyLimits(settings, OffsetDateTime.now());
    }

    private void applyLimits(SiteSettings settings, OffsetDateTime now) {
        historyRepository.deleteExpired(now.minusDays(settings.getHistoryRetentionDays()));
        historyRepository.trimToMaxEntriesPerUser(settings.getHistoryMaxEntries());
    }

    public record HistoryDto(Long id, Long bookId, String title, String coverUrl, OffsetDateTime viewedAt) {
        static HistoryDto from(BookViewHistory history) {
            Long bookId = history.getBook().getId();
            return new HistoryDto(history.getId(), bookId, history.getBook().getTitle(),
                    "/api/books/" + bookId + "/cover", history.getViewedAt());
        }
    }
}
