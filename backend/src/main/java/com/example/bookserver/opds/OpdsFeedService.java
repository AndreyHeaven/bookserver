package com.example.bookserver.opds;

import com.example.bookserver.authors.AuthorsService;
import com.example.bookserver.authors.dto.AlphabetBucketDto;
import com.example.bookserver.authors.dto.AuthorCardDto;
import com.example.bookserver.authors.dto.AuthorDetailsDto;
import com.example.bookserver.authors.dto.AuthorSearchRequest;
import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookAuthor;
import com.example.bookserver.domain.BookFile;
import com.example.bookserver.domain.BookList;
import com.example.bookserver.domain.BookListItem;
import com.example.bookserver.domain.Genre;
import com.example.bookserver.genres.GenresService;
import com.example.bookserver.genres.dto.GenreDetailsDto;
import com.example.bookserver.genres.dto.GenreNodeDto;
import com.example.bookserver.lists.ListAccessGuard;
import com.example.bookserver.repo.BookListRepository;
import com.example.bookserver.repo.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds every OPDS feed (root navigation plus the New / Authors / Genres /
 * Lists branches) by reusing the existing domain services and repositories.
 * All methods are read-only and load lazy associations inside the transaction.
 */
@Service
@Transactional(readOnly = true)
public class OpdsFeedService {

    /** Books/authors per acquisition or navigation page. */
    static final int PAGE_SIZE = 30;

    private final BookRepository bookRepository;
    private final AuthorsService authorsService;
    private final GenresService genresService;
    private final BookListRepository bookListRepository;
    private final ListAccessGuard listGuard;

    public OpdsFeedService(BookRepository bookRepository,
                           AuthorsService authorsService,
                           GenresService genresService,
                           BookListRepository bookListRepository,
                           ListAccessGuard listGuard) {
        this.bookRepository = bookRepository;
        this.authorsService = authorsService;
        this.genresService = genresService;
        this.bookListRepository = bookListRepository;
        this.listGuard = listGuard;
    }

    // ---------------------------------------------------------------- root

    /** Root navigation feed: entry points into every catalog branch. */
    public OpdsFeed root() {
        List<OpdsEntry> entries = List.of(
                navEntry("root:new", "Новинки", "Недавно добавленные книги",
                        OpdsConstants.BASE_PATH + "/new", OpdsConstants.ACQUISITION_TYPE, OpdsConstants.REL_SORT_NEW),
                navEntry("root:authors", "Авторы", "Каталог по авторам",
                        OpdsConstants.BASE_PATH + "/authors", OpdsConstants.NAVIGATION_TYPE, OpdsConstants.REL_SUBSECTION),
                navEntry("root:genres", "Жанры", "Каталог по жанрам",
                        OpdsConstants.BASE_PATH + "/genres", OpdsConstants.NAVIGATION_TYPE, OpdsConstants.REL_SUBSECTION),
                navEntry("root:lists", "Списки", "Мои списки книг",
                        OpdsConstants.BASE_PATH + "/lists", OpdsConstants.NAVIGATION_TYPE, OpdsConstants.REL_SUBSECTION));
        return navFeed("root", "Каталог книг", OpdsConstants.BASE_PATH, null, entries);
    }

    // ----------------------------------------------------------------- new

    /** Acquisition feed of the most recently imported books. */
    public OpdsFeed newBooks(int page) {
        Page<Book> books = bookRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable(page));
        List<OpdsEntry> entries = books.getContent().stream().map(this::bookEntry).toList();
        String base = OpdsConstants.BASE_PATH + "/new";
        return acquisitionFeed("new", "Новинки", base, OpdsConstants.BASE_PATH, page, books, entries);
    }

    // -------------------------------------------------------------- authors

    /** Navigation feed of alphabet buckets for browsing authors. */
    public OpdsFeed authorsRoot() {
        List<OpdsEntry> entries = authorsService.alphabet().stream()
                .filter(bucket -> bucket.letter() != null && !bucket.letter().isBlank())
                .map(this::alphabetEntry)
                .toList();
        return navFeed("authors", "Авторы", OpdsConstants.BASE_PATH + "/authors",
                OpdsConstants.BASE_PATH, entries);
    }

    /** Navigation feed listing authors whose surname starts with {@code letter}. */
    public OpdsFeed authorsByLetter(String letter, int page) {
        AuthorSearchRequest request = new AuthorSearchRequest(null, letter, page, PAGE_SIZE, null);
        Page<AuthorCardDto> authors = authorsService.search(request);
        List<OpdsEntry> entries = authors.getContent().stream().map(this::authorEntry).toList();
        String base = OpdsConstants.BASE_PATH + "/authors/letter/" + encode(letter);
        return navFeedPaged("authors:letter:" + letter, "Авторы на «" + letter + "»",
                base, OpdsConstants.BASE_PATH + "/authors", page, authors, entries);
    }

    /** Acquisition feed of all books by a single author. */
    public OpdsFeed authorBooks(Long authorId, int page) {
        AuthorDetailsDto author = authorsService.details(authorId);
        Page<Book> books = bookRepository.findByAuthorId(authorId, pageable(page));
        List<OpdsEntry> entries = books.getContent().stream().map(this::bookEntry).toList();
        String base = OpdsConstants.BASE_PATH + "/authors/" + authorId;
        return acquisitionFeed("author:" + authorId, author.fullName(), base,
                OpdsConstants.BASE_PATH + "/authors", page, books, entries);
    }

    // --------------------------------------------------------------- genres

    /** Navigation feed of the top-level genres. */
    public OpdsFeed genresRoot() {
        List<OpdsEntry> entries = genresService.tree().stream()
                .map(this::genreEntry)
                .toList();
        return navFeed("genres", "Жанры", OpdsConstants.BASE_PATH + "/genres",
                OpdsConstants.BASE_PATH, entries);
    }

    /**
     * Feed for a single genre: sub-genres as navigation entries (first page only)
     * followed by the books tagged directly with this genre.
     */
    public OpdsFeed genre(Long genreId, int page) {
        GenreDetailsDto genre = genresService.details(genreId);
        Page<Book> books = bookRepository.findByGenreId(genreId, pageable(page));

        List<OpdsEntry> entries = new ArrayList<>();
        if (page == 0) {
            genre.children().forEach(child -> entries.add(genreEntry(child)));
        }
        books.getContent().forEach(book -> entries.add(bookEntry(book)));

        String base = OpdsConstants.BASE_PATH + "/genres/" + genreId;
        return acquisitionFeed("genre:" + genreId, genre.title(), base,
                OpdsConstants.BASE_PATH + "/genres", page, books, entries);
    }

    // ---------------------------------------------------------------- lists

    /** Navigation feed of the current user's book lists. */
    public OpdsFeed lists(int page) {
        Page<BookList> lists = bookListRepository.findByOwnerId(listGuard.currentUserId(), pageable(page));
        List<OpdsEntry> entries = lists.getContent().stream().map(this::listEntry).toList();
        return navFeedPaged("lists", "Мои списки", OpdsConstants.BASE_PATH + "/lists",
                OpdsConstants.BASE_PATH, page, lists, entries);
    }

    /** Acquisition feed of the books contained in one of the user's lists. */
    public OpdsFeed listBooks(Long listId) {
        BookList list = listGuard.requireOwnedList(listId);
        List<OpdsEntry> entries = list.getItems().stream()
                .sorted(Comparator.comparingInt(BookListItem::getPosition))
                .map(BookListItem::getBook)
                .filter(book -> !book.isDeleted())
                .map(this::bookEntry)
                .toList();
        String base = OpdsConstants.BASE_PATH + "/lists/" + listId;
        return new OpdsFeed(urn("list:" + listId), list.getTitle(), now(), base,
                OpdsConstants.ACQUISITION_TYPE, OpdsConstants.BASE_PATH + "/lists", null, null, entries);
    }

    // ------------------------------------------------------------- entries

    private OpdsEntry bookEntry(Book book) {
        List<String> authors = book.getAuthors().stream()
                .sorted(Comparator.comparingInt(BookAuthor::getPosition))
                .map(ba -> fullName(ba.getPerson().getLastName(),
                        ba.getPerson().getFirstName(),
                        ba.getPerson().getMiddleName()))
                .filter(name -> !name.isBlank())
                .toList();
        List<String> categories = book.getGenres().stream()
                .map(Genre::getTitle)
                .sorted()
                .toList();
        String content = book.getAnnotation() == null ? null : book.getAnnotation().getBody();
        List<OpdsLink> links = book.getFiles().stream()
                .sorted(Comparator.comparing(BookFile::getId))
                .map(this::acquisitionLink)
                .toList();
        return new OpdsEntry(urn("book:" + book.getId()), book.getTitle(),
                book.getUpdatedAt(), content, authors, categories, links);
    }

    private OpdsLink acquisitionLink(BookFile file) {
        String href = OpdsConstants.BASE_PATH + "/books/" + file.getBook().getId() + "/files/" + file.getId();
        String type = OpdsConstants.acquisitionType(file.getFormat());
        String title = file.getFormat() == null ? "Скачать"
                : "Скачать " + file.getFormat().toUpperCase(Locale.ROOT);
        return new OpdsLink(OpdsConstants.REL_ACQUISITION, href, type, title);
    }

    private OpdsEntry alphabetEntry(AlphabetBucketDto bucket) {
        return navEntry("authors:letter:" + bucket.letter(), bucket.letter(),
                bucket.count() + " авт.",
                OpdsConstants.BASE_PATH + "/authors/letter/" + encode(bucket.letter()),
                OpdsConstants.NAVIGATION_TYPE, OpdsConstants.REL_SUBSECTION);
    }

    private OpdsEntry authorEntry(AuthorCardDto author) {
        return navEntry("author:" + author.id(), author.fullName(),
                author.bookCount() + " кн.",
                OpdsConstants.BASE_PATH + "/authors/" + author.id(),
                OpdsConstants.ACQUISITION_TYPE, OpdsConstants.REL_SUBSECTION);
    }

    private OpdsEntry genreEntry(GenreNodeDto genre) {
        return navEntry("genre:" + genre.id(), genre.title(),
                genre.bookCount() + " кн.",
                OpdsConstants.BASE_PATH + "/genres/" + genre.id(),
                OpdsConstants.ACQUISITION_TYPE, OpdsConstants.REL_SUBSECTION);
    }

    private OpdsEntry listEntry(BookList list) {
        return navEntry("list:" + list.getId(), list.getTitle(), list.getDescription(),
                OpdsConstants.BASE_PATH + "/lists/" + list.getId(),
                OpdsConstants.ACQUISITION_TYPE, OpdsConstants.REL_SUBSECTION);
    }

    // -------------------------------------------------------------- helpers

    private OpdsEntry navEntry(String idSuffix, String title, String content,
                               String href, String targetType, String rel) {
        return new OpdsEntry(urn(idSuffix), title, now(), content, List.of(), List.of(),
                List.of(new OpdsLink(rel, href, targetType)));
    }

    private OpdsFeed navFeed(String idSuffix, String title, String selfHref,
                             String upHref, List<OpdsEntry> entries) {
        return new OpdsFeed(urn(idSuffix), title, now(), selfHref,
                OpdsConstants.NAVIGATION_TYPE, upHref, null, null, entries);
    }

    private OpdsFeed navFeedPaged(String idSuffix, String title, String base, String upHref,
                                  int page, Page<?> pageData, List<OpdsEntry> entries) {
        return new OpdsFeed(urn(idSuffix), title, now(), pageHref(base, page),
                OpdsConstants.NAVIGATION_TYPE, upHref,
                prevHref(base, page), nextHref(base, page, pageData), entries);
    }

    private OpdsFeed acquisitionFeed(String idSuffix, String title, String base, String upHref,
                                     int page, Page<?> pageData, List<OpdsEntry> entries) {
        return new OpdsFeed(urn(idSuffix), title, now(), pageHref(base, page),
                OpdsConstants.ACQUISITION_TYPE, upHref,
                prevHref(base, page), nextHref(base, page, pageData), entries);
    }

    private static Pageable pageable(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }

    private static String pageHref(String base, int page) {
        return page <= 0 ? base : base + "?page=" + page;
    }

    private static String prevHref(String base, int page) {
        return page > 0 ? pageHref(base, page - 1) : null;
    }

    private static String nextHref(String base, int page, Page<?> pageData) {
        return page + 1 < pageData.getTotalPages() ? pageHref(base, page + 1) : null;
    }

    private static String urn(String suffix) {
        return OpdsConstants.URN_PREFIX + suffix;
    }

    private static String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static String fullName(String lastName, String firstName, String middleName) {
        return String.join(" ", List.of(nullToBlank(lastName), nullToBlank(firstName), nullToBlank(middleName)))
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
