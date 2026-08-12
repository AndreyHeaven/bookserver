package com.example.bookserver.imports;

import com.example.bookserver.domain.Annotation;
import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookAuthor;
import com.example.bookserver.domain.BookFile;
import com.example.bookserver.domain.BookSeriesMember;
import com.example.bookserver.domain.Genre;
import com.example.bookserver.domain.Person;
import com.example.bookserver.domain.Series;
import com.example.bookserver.repo.BookFileRepository;
import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.repo.GenreRepository;
import com.example.bookserver.repo.PersonRepository;
import com.example.bookserver.repo.SeriesRepository;
import com.example.bookserver.storage.CoverStorage;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.bookserver.config.CacheConfig;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ImportedBookWriter {

    private static final Logger log = LoggerFactory.getLogger(ImportedBookWriter.class);
    private static final int BOOK_TITLE_MAX_LENGTH = 1_024;
    private static final int PERSON_NAME_MAX_LENGTH = 128;
    private static final int SERIES_TITLE_MAX_LENGTH = 512;
    private static final int LANGUAGE_MAX_LENGTH = 8;

    private final BookRepository bookRepository;
    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final SeriesRepository seriesRepository;
    private final BookFileRepository bookFileRepository;
    private final CoverStorage coverStorage;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;

    public ImportedBookWriter(BookRepository bookRepository,
                              PersonRepository personRepository,
                              GenreRepository genreRepository,
                              SeriesRepository seriesRepository,
                              BookFileRepository bookFileRepository,
                              CoverStorage coverStorage,
                              EntityManager entityManager,
                              CacheManager cacheManager) {
        this.bookRepository = bookRepository;
        this.personRepository = personRepository;
        this.genreRepository = genreRepository;
        this.seriesRepository = seriesRepository;
        this.bookFileRepository = bookFileRepository;
        this.coverStorage = coverStorage;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
    }

    @Transactional
    public Book write(ImportedBook imported) {
        String md5 = imported.storedFile().md5();
        Book book = bookRepository.findByMd5(md5).orElseGet(Book::new);
        boolean isNewBook = book.getId() == null;
        book.setTitle(limit("book title", imported.title(), BOOK_TITLE_MAX_LENGTH));
        book.setLang(normalizeLanguage(imported.lang()));
        book.setYear(imported.year());
        book.setKeywords(imported.keywords());
        book.setFileType(imported.fileType());
        book.setFileSize(imported.storedFile().size());
        book.setMd5(md5);
        book.setArchiveName(imported.archiveName());
        book.setInpxSource(imported.inpxSource());
        book.setDeleted(false);
        // Only extract/store a cover for brand-new books; on dedup by md5 leave it untouched.
        if (isNewBook && imported.coverImage() != null && imported.coverImage().length > 0) {
            try {
                String coverPath = coverStorage.store(imported.coverImage(), imported.coverContentType());
                book.setCoverPath(coverPath);
                book.setCoverContentType(imported.coverContentType());
            } catch (IOException e) {
                log.warn("Failed to store cover for book '{}': {}", imported.title(), e.getMessage());
            }
        }
        book = bookRepository.saveAndFlush(book);

        // Clear composite-key associations and flush the resulting DELETEs before
        // re-adding, otherwise re-importing the same book (dedup by md5) re-inserts
        // rows with identical embedded ids and Hibernate raises ObjectDeletedException.
        book.getAuthors().clear();
        book.getSeriesMembers().clear();
        book.getGenres().clear();
        bookRepository.flush();

        int position = 0;
        for (ImportedAuthor author : imported.authors()) {
            Person person = upsertPerson(author);
            book.getAuthors().add(new BookAuthor(book, person, position++));
        }

        Set<Genre> genres = new LinkedHashSet<>();
        for (String code : imported.genreCodes()) {
            genreRepository.findByCode(code).ifPresent(genres::add);
        }
        book.getGenres().addAll(genres);

        String seriesTitle = imported.series() == null ? null
                : limit("series title", imported.series().title(), SERIES_TITLE_MAX_LENGTH);
        if (seriesTitle != null) {
            Series series = seriesRepository.findByTitle(seriesTitle)
                    .orElseGet(() -> {
                        Series s = new Series();
                        s.setTitle(seriesTitle);
                        return seriesRepository.saveAndFlush(s);
                    });
            book.getSeriesMembers().add(new BookSeriesMember(book, series, imported.series().sequenceNumber()));
        }

        if (imported.annotation() != null && !imported.annotation().isBlank()) {
            Annotation annotation = book.getAnnotation();
            if (annotation == null) {
                annotation = new Annotation(book, imported.annotation());
                book.setAnnotation(annotation);
            } else {
                annotation.setBody(imported.annotation());
            }
        }

        String storagePath = imported.storedFile().path();
        String entryName = imported.entryName();
        boolean fileExists = (entryName == null
                ? bookFileRepository.findByStoragePath(storagePath)
                : bookFileRepository.findByStoragePathAndEntryName(storagePath, entryName))
                .isPresent();
        if (!fileExists) {
            BookFile file = new BookFile();
            file.setBook(book);
            file.setFormat(imported.fileType().toLowerCase(Locale.ROOT));
            file.setStoragePath(storagePath);
            file.setEntryName(entryName);
            file.setSizeBytes(imported.storedFile().size());
            book.getFiles().add(file);
        }
        Book saved = bookRepository.saveAndFlush(book);
        entityManager.refresh(saved);
        evictSearchCachesAfterCommit();
        return saved;
    }

    private void evictSearchCachesAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheManager.getCache(CacheConfig.BOOK_FACETS_CACHE).clear();
                cacheManager.getCache(CacheConfig.GENRES_TREE_CACHE).clear();
            }
        });
    }

    private Person upsertPerson(ImportedAuthor author) {
        String lastName = limit("author last name", author.lastName(), PERSON_NAME_MAX_LENGTH);
        String firstName = limit("author first name", author.firstName(), PERSON_NAME_MAX_LENGTH);
        String middleName = limit("author middle name", author.middleName(), PERSON_NAME_MAX_LENGTH);
        return personRepository.findByLastNameAndFirstNameAndMiddleName(lastName, firstName, middleName)
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setLastName(lastName);
                    p.setFirstName(firstName);
                    p.setMiddleName(middleName);
                    return personRepository.saveAndFlush(p);
                });
    }

    private static String normalizeLanguage(String value) {
        String language = clean(value);
        if (language == null || language.length() > LANGUAGE_MAX_LENGTH
                || !language.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,4})?")) {
            if (language != null) {
                log.warn("Ignoring invalid FB2 language value: {}", abbreviate(language));
            }
            return null;
        }
        return language.toLowerCase(Locale.ROOT);
    }

    private static String limit(String field, String value, int maxLength) {
        String cleaned = clean(value);
        if (cleaned == null || cleaned.length() <= maxLength) {
            return cleaned;
        }
        log.warn("Truncating {} from {} to {} characters: {}", field, cleaned.length(), maxLength,
                abbreviate(cleaned));
        return cleaned.substring(0, maxLength);
    }

    private static String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
