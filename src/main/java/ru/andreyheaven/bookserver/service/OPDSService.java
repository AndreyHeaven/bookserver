package ru.andreyheaven.bookserver.service;

import com.rometools.rome.feed.atom.*;
import com.rometools.rome.feed.module.*;
import com.rometools.rome.feed.synd.*;
import org.springframework.data.domain.*;
import org.springframework.data.util.*;
import org.springframework.stereotype.*;
import ru.andreyheaven.bookserver.domain.*;
import ru.andreyheaven.bookserver.repository.*;
import java.util.*;
import java.util.stream.*;

@Service
public class OPDSService {
    public static final String PROFILE_OPDS_CATALOG = "application/atom+xml;profile=opds-catalog";
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;

    public OPDSService(AuthorRepository authorRepository, BookRepository bookRepository, GenreRepository genreRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
    }

    public Feed createOpds() {
        Feed feed = getFeed();
        feed.setEntries(List.of(getIndexAuthorsEntry(), getIndexNewEntry(), getIndexGenresEntry()));
        return feed;
    }

    private Entry getIndexNewEntry() {
        final Entry entry1 = new Entry();
        final Content contentEntry1 = new Content();
        contentEntry1.setValue("Новые поступления за неделю");
        entry1.setSummary(contentEntry1);
        entry1.setTitle("Новинки");
        entry1.setId("tag:root:new");
        final Link link11 = new Link();
        link11.setHref("/opds/new");
        link11.setRel(null);
        //<link href="/opds/new" rel="http://opds-spec.org/sort/new" type="application/atom+xml;profile=opds-catalog"/>
        final Link link12 = new Link();
        link12.setHref("/opds/new");
        link12.setRel("http://opds-spec.org/sort/new");
        link12.setType(PROFILE_OPDS_CATALOG);
        entry1.setOtherLinks(List.of(link11, link12));
        return entry1;
    }

    private Entry getIndexAuthorsEntry() {
        final Entry entry = new Entry();
        final Content contentEntry = new Content();
        contentEntry.setValue("Поиск книг по авторам");
        entry.setSummary(contentEntry);
        entry.setTitle("По авторам");
        entry.setId("tag:root:authors");
        final Link link1 = new Link();
        link1.setHref("/opds/authorsindex");
        link1.setRel(null);
        link1.setType(PROFILE_OPDS_CATALOG);
        entry.setOtherLinks(List.of(link1));
        return entry;
    }

    private Entry getIndexGenresEntry() {
        final Entry entry = new Entry();
        final Content contentEntry = new Content();
        contentEntry.setValue("Поиск книг по жанрам");
        entry.setSummary(contentEntry);
        entry.setTitle("По жанрам");
        entry.setId("tag:root:genre");
        final Link link1 = new Link();
        link1.setHref("/opds/genres");
        link1.setRel(null);
        link1.setType(PROFILE_OPDS_CATALOG);
        entry.setOtherLinks(List.of(link1));
        return entry;
    }

    public Feed createAuthorIndex(String author) {
        Feed feed = getFeed();
        author = author == null ? "" : author.toUpperCase(Locale.ROOT);
        final List<Pair<String, Integer>> prefixes = authorRepository.findPrefixes(author);
        feed.setEntries(prefixes.stream()
                .map(i -> {
                    /*
                     * <entry> <updated>2022-01-25T04:26:49+01:00</updated>
                     *  <id>tag:authors:D</id>
                     *  <title>D</title>
                     *  <content type="text">95 авторов на &#039;D&#039;</content>
                     *  <link href="/opds/authors/D" type="application/atom+xml;profile=opds-catalog" />
                     * </entry>
                     */
                    final String letter = i.getFirst();
                    final Integer count = i.getSecond();

                    final Entry entry = new Entry();
                    final Content contentEntry = new Content();
                    contentEntry.setValue(count + " авторов на " + letter);
                    entry.setContents(List.of(contentEntry));
                    entry.setTitle(letter);
                    entry.setId("tag:authors:" + letter);
                    final Link e1 = new Link();
                    if (count >= 100) {
                        e1.setHref("/opds/authorsindex/" + letter);
                    } else {
                        e1.setHref("/opds/authors/" + letter);
                    }
                    e1.setType(PROFILE_OPDS_CATALOG);
                    entry.setOtherLinks(List.of(e1));
                    return entry;
                }).collect(Collectors.toList()));


        return feed;
    }

    private Feed getFeed() {
        Feed feed = new Feed();
        feed.setFeedType("atom_1.0");
        feed.setTitle("Book Server OPDS catalog");
        final Link startLink = new Link();
        startLink.setHref("/opds");
        startLink.setRel("start");
        startLink.setType(PROFILE_OPDS_CATALOG);
        final Link selfLink = new Link();
        selfLink.setHref("/opds");
        selfLink.setRel("self");
        selfLink.setType(PROFILE_OPDS_CATALOG);
        //TODO
        //    <link href="/opds-opensearch.xml" rel="search" type="application/opensearchdescription+xml"/>
        //    <link href="/opds/search?searchTerm={searchTerms}" rel="search" type="application/atom+xml"/>
        feed.setOtherLinks(new LinkedList<>(List.of(startLink, selfLink)));
        return feed;
    }

    public Feed getAuthors(String author) {
        Feed feed = getFeed();
        author = author == null ? "" : author.toUpperCase(Locale.ROOT);
        final List<Author> authors = authorRepository.findBySurnameStartsWith(author);
        feed.setEntries(authors.stream()
                .map(i -> {
                    /*
                     * <entry> <updated>2022-01-25T04:26:49+01:00</updated>
                     *  <id>tag:authors:D</id>
                     *  <title>D</title>
                     *  <content type="text">95 авторов на &#039;D&#039;</content>
                     *  <link href="/opds/authors/D" type="application/atom+xml;profile=opds-catalog" />
                     * </entry>
                     */
                    final String fullName = i.getFullName();

                    final Entry entry = new Entry();
//                    final Content contentEntry = new Content();
//                    contentEntry.setValue(i.get("count") + " авторов на " + letter);
//                    entry.setContents(List.of(contentEntry));
                    entry.setTitle(fullName);
                    entry.setId("tag:author:" + i.getId());
                    final Link e1 = new Link();
                    e1.setHref("/opds/author/" + i.getId());
                    e1.setRel(null);
                    e1.setType(PROFILE_OPDS_CATALOG);
//                    final Link e2 = new Link();
//                    e2.setHref("/opds/author/" + i.getId()+"/alphabet");
//                    e2.setTitle("Книги автора по алфавиту");
//                    e2.setType("application/atom+xml;profile=opds-catalog");
                    entry.setOtherLinks(List.of(e1));
                    return entry;
                }).collect(Collectors.toList()));


        return feed;
    }

    public Feed getAuthor(Integer id) {
        Feed feed = getFeed();
        final Optional<Author> byId = authorRepository.findById(id);

        feed.setEntries(byId.map(author -> {
            final Entry bio = new Entry();
            bio.setId("tag:author:bio:" + author.getId());
            bio.setTitle("Об авторе");
            final Content content = new Content();
            content.setType("text/html");
            content.setValue(author.getFullName()); //TODO тут должно быть что-то типа биографии
            bio.setSummary(content);

            final Entry alphabet = new Entry();
            alphabet.setId("tag:author:" + author.getId() + ":alphabet");
            alphabet.setTitle("Книги по алфавиту");
            final Link alphabetLink = new Link();
            alphabetLink.setRel(null);
            alphabetLink.setHref("/opds/author/" + author.getId() + "/alphabet");
            alphabetLink.setType(PROFILE_OPDS_CATALOG);
            alphabet.setAlternateLinks(List.of(alphabetLink));
            return List.of(bio, alphabet);
        }).orElse(List.of()));
        return feed;
    }

    public Feed getBooksByAlphabet(Integer authorId) {
        Feed feed = getFeed();
        final List<Book> books = bookRepository.findByAuthor(authorId, Integer.MAX_VALUE, 0);
        feed.setEntries(books.stream().map(this::createBookEntry).toList());
        return feed;
    }

    private Entry createBookEntry(Book book) {
        final Entry entry = new Entry();
        entry.setTitle(book.getTitle());
        entry.setId("tag:book:" + book.getId());
        entry.setAuthors(Stream.of(book.getAuthors()).map(authorRepository::findById).map(Optional::orElseThrow).map(author -> {
            SyndPerson person = new Person();
            person.setName(author.getFullName());
            person.setUri("/a/" + author.getId().toString());
            return person;
        }).toList());
        entry.setCategories(Stream.of(book.getGenres()).map(genreRepository::findById).map(Optional::orElseThrow).map(genre -> {
            Category category = new Category();
            category.setTerm(genre.getCode());
            category.setLabel(genre.getTitle());
            return category;
        }).toList());
        final DCModuleImpl dcModule = new DCModuleImpl();
        dcModule.setLanguage(book.getLang());
        dcModule.setFormat(book.getFormat());
        entry.setModules(List.of(dcModule));
        final Link downloadLink = new Link();
        // <link href="/b/478378/fb2" rel="http://opds-spec.org/acquisition/open-access" type="application/fb2+zip" />
        downloadLink.setHref("/b/%s/%s".formatted(book.getFileID(), book.getFormat()));
        downloadLink.setRel("http://opds-spec.org/acquisition/open-access");
        downloadLink.setType("application/fb2+zip");
        downloadLink.setTitle("Скачать " + book.getFormat());
        entry.setOtherLinks(List.of(downloadLink));
        // <link href="/b/478378" rel="alternate" type="text/html" title="Книга на сайте" />
        final Link alternateLink = new Link();
        alternateLink.setHref("/b/%s".formatted(book.getFileID()));
        alternateLink.setRel("alternate");
        alternateLink.setType("text/html");
        alternateLink.setTitle("Книга на сайте");
        entry.setAlternateLinks(List.of(alternateLink));
        return entry;
    }

    public Feed getGenres(String code) {
        Feed feed = getFeed();
        List<Genre> genres = code == null ? genreRepository.findParentGenres() : genreRepository.findByParent(code);
        feed.setEntries(genres.stream().map(genre -> createGenreEntry(genre, code != null)).toList());
        return feed;
    }

    /**
     * <entry> <updated>2022-02-14T08:29:04+01:00</updated>
     * <id>tag:root:genre:Детективы и Триллеры</id>
     * <title>Детективы и Триллеры</title>
     * <content type="text">Книги в жанре Детективы и Триллеры</content>
     * <link href="/opds/genres/%D0%94%D0%B5%D1%82%D0%B5%D0%BA%D1%82%D0%B8%D0%B2%D1%8B%20%D0%B8%20%D0%A2%D1%80%D0%B8%D0%BB%D0%BB%D0%B5%D1%80%D1%8B" type="application/atom+xml;profile=opds-catalog" />
     * </entry>
     *
     * @param genre
     * @param b
     * @return
     */
    private Entry createGenreEntry(Genre genre, boolean books) {
        final Entry entry = new Entry();
        entry.setTitle(genre.getTitle());
        entry.setId("tag:root:genre:" + genre.getTitle());
        final Content summary = new Content();
        summary.setValue("Книги в жанре " + genre.getTitle());
        entry.setSummary(summary);
        final Link alternateLink = new Link();
        if (books)
            alternateLink.setHref("/opds/books?genre=%s".formatted(genre.getCode()));
        else
            alternateLink.setHref("/opds/genres/%s".formatted(genre.getCode()));
        alternateLink.setType(PROFILE_OPDS_CATALOG);
        entry.setAlternateLinks(List.of(alternateLink));
        return entry;
    }

    public Feed getBooks(String genreCode, Integer authorId, Pageable page) {
        Feed feed = getFeed();
// <link href="/opds/genres/%D0%A4%D0%B0%D0%BD%D1%82%D0%B0%D1%81%D1%82%D0%B8%D0%BA%D0%B0" rel="up" type="application/atom+xml;profile=opds-catalog" />
// <link href="/opds/genres/%D0%A4%D0%B0%D0%BD%D1%82%D0%B0%D1%81%D1%82%D0%B8%D0%BA%D0%B0/3/1" rel="next" type="application/atom+xml;profile=opds-catalog" />
        final List<Book> books;
        if (genreCode != null) {
            final Link next = new Link();
            next.setType(PROFILE_OPDS_CATALOG);
            next.setRel("next");
            next.setHref("/opds/books?genre=%s&page=%d".formatted(genreCode, page.next().getPageNumber()));
            feed.getOtherLinks().add(next);

            books = bookRepository.findByGenre(genreCode, page.getPageSize(), page.getOffset());
        } else if (authorId != null) {
            final Link next = new Link();
            next.setType(PROFILE_OPDS_CATALOG);
            next.setRel("next");
            next.setHref("/opds/books?author=%d&page=%d".formatted(authorId, page.next().getPageNumber()));
            feed.getOtherLinks().add(next);

            books = bookRepository.findByAuthor(authorId, page.getPageSize(), page.getOffset());
        } else books = List.of();
        feed.setEntries(books.stream().map(this::createBookEntry).toList());
        return feed;
    }
}
