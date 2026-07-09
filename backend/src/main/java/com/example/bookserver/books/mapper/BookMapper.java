package com.example.bookserver.books.mapper;

import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookFileDto;
import com.example.bookserver.books.dto.BookSeriesDto;
import com.example.bookserver.books.dto.GenreBriefDto;
import com.example.bookserver.books.dto.PersonBriefDto;
import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookAuthor;
import com.example.bookserver.domain.BookFile;
import com.example.bookserver.domain.BookSeriesMember;
import com.example.bookserver.domain.BookTranslator;
import com.example.bookserver.domain.Genre;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class BookMapper {

    public BookCardDto toCard(Book book) {
        return new BookCardDto(
                book.getId(),
                book.getTitle(),
                authors(book),
                book.getYear(),
                book.getLang(),
                book.getFileType(),
                !book.getFiles().isEmpty(),
                coverUrl(book));
    }

    public BookDetailsDto toDetails(Book book) {
        return new BookDetailsDto(
                book.getId(),
                book.getTitle(),
                authors(book),
                translators(book),
                book.getYear(),
                book.getLang(),
                book.getFileType(),
                book.getFileSize(),
                book.getAnnotation() == null ? null : book.getAnnotation().getBody(),
                genres(book),
                series(book),
                files(book),
                coverUrl(book));
    }

    private static String coverUrl(Book book) {
        return book.getCoverPath() == null ? null : "/api/books/" + book.getId() + "/cover";
    }

    /** Public accessor so other domains (e.g. book lists) can render authors consistently. */
    public List<PersonBriefDto> authorBriefs(Book book) {
        return authors(book);
    }

    private List<PersonBriefDto> authors(Book book) {
        return book.getAuthors().stream()
                .sorted(Comparator.comparingInt(BookAuthor::getPosition))
                .map(a -> new PersonBriefDto(a.getPerson().getId(), fullName(
                        a.getPerson().getLastName(),
                        a.getPerson().getFirstName(),
                        a.getPerson().getMiddleName())))
                .toList();
    }

    private List<PersonBriefDto> translators(Book book) {
        return book.getTranslators().stream()
                .sorted(Comparator.comparingInt(BookTranslator::getPosition))
                .map(t -> new PersonBriefDto(t.getPerson().getId(), fullName(
                        t.getPerson().getLastName(),
                        t.getPerson().getFirstName(),
                        t.getPerson().getMiddleName())))
                .toList();
    }

    private List<GenreBriefDto> genres(Book book) {
        return book.getGenres().stream()
                .sorted(Comparator.comparing(Genre::getTitle))
                .map(g -> new GenreBriefDto(g.getId(), g.getCode(), g.getTitle(), genrePath(g)))
                .toList();
    }

    private List<BookSeriesDto> series(Book book) {
        return book.getSeriesMembers().stream()
                .sorted(Comparator
                        .comparing((BookSeriesMember m) -> m.getSeries().getTitle())
                        .thenComparing(m -> m.getSequenceNumber() == null ? Integer.MAX_VALUE : m.getSequenceNumber()))
                .map(m -> new BookSeriesDto(m.getSeries().getId(), m.getSeries().getTitle(), m.getSequenceNumber()))
                .toList();
    }

    private List<BookFileDto> files(Book book) {
        return book.getFiles().stream()
                .sorted(Comparator.comparing(BookFile::getId))
                .map(f -> new BookFileDto(
                        f.getId(),
                        f.getFormat(),
                        f.getSizeBytes(),
                        "/api/books/" + book.getId() + "/files/" + f.getId()))
                .toList();
    }

    private static String fullName(String lastName, String firstName, String middleName) {
        return String.join(" ", List.of(
                        nullToBlank(lastName),
                        nullToBlank(firstName),
                        nullToBlank(middleName))).trim()
                .replaceAll("\\s+", " ");
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static List<String> genrePath(Genre genre) {
        List<String> path = new ArrayList<>();
        Genre cursor = genre;
        while (cursor != null) {
            path.add(0, cursor.getTitle());
            cursor = cursor.getParent();
        }
        return path;
    }
}
