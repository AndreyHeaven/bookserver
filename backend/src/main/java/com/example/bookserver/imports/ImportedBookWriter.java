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
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ImportedBookWriter {

    private final BookRepository bookRepository;
    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final SeriesRepository seriesRepository;
    private final BookFileRepository bookFileRepository;
    private final EntityManager entityManager;

    public ImportedBookWriter(BookRepository bookRepository,
                              PersonRepository personRepository,
                              GenreRepository genreRepository,
                              SeriesRepository seriesRepository,
                              BookFileRepository bookFileRepository,
                              EntityManager entityManager) {
        this.bookRepository = bookRepository;
        this.personRepository = personRepository;
        this.genreRepository = genreRepository;
        this.seriesRepository = seriesRepository;
        this.bookFileRepository = bookFileRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Book write(ImportedBook imported) {
        String md5 = imported.storedFile().md5();
        Book book = bookRepository.findByMd5(md5).orElseGet(Book::new);
        book.setTitle(imported.title());
        book.setLang(imported.lang());
        book.setYear(imported.year());
        book.setKeywords(imported.keywords());
        book.setFileType(imported.fileType());
        book.setFileSize(imported.storedFile().size());
        book.setMd5(md5);
        book.setArchiveName(imported.archiveName());
        book.setInpxSource(imported.inpxSource());
        book.setDeleted(false);
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

        if (imported.series() != null && imported.series().title() != null && !imported.series().title().isBlank()) {
            Series series = seriesRepository.findByTitle(imported.series().title())
                    .orElseGet(() -> {
                        Series s = new Series();
                        s.setTitle(imported.series().title());
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
        return saved;
    }

    private Person upsertPerson(ImportedAuthor author) {
        return personRepository.findByLastNameAndFirstNameAndMiddleName(
                        clean(author.lastName()), clean(author.firstName()), clean(author.middleName()))
                .orElseGet(() -> {
                    Person p = new Person();
                    p.setLastName(clean(author.lastName()));
                    p.setFirstName(clean(author.firstName()));
                    p.setMiddleName(clean(author.middleName()));
                    return personRepository.saveAndFlush(p);
                });
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
