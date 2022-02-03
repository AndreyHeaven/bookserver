package ru.andreyheaven.bookserver.service;

import com.rometools.rome.feed.atom.*;
import com.rometools.rome.feed.module.*;
import com.rometools.rome.feed.synd.*;
import org.springframework.data.util.*;
import org.springframework.stereotype.*;
import ru.andreyheaven.bookserver.domain.*;
import ru.andreyheaven.bookserver.repository.*;
import javax.transaction.*;
import java.util.*;
import java.util.stream.*;

@Service
public class OPDSService {
    public static final String PROFILE_OPDS_CATALOG = "application/atom+xml;profile=opds-catalog";
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public OPDSService(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    public Feed createOpds() {
        Feed feed = getFeed();
        final Entry entry = new Entry();
        final Content contentEntry = new Content();
        contentEntry.setValue("Поиск книг по авторам");
        entry.setContents(List.of(contentEntry));
        entry.setTitle("По авторам");
        entry.setId("tag:root:authors");
        final Link link1 = new Link();
        link1.setHref("/opds/authorsindex");
        link1.setRel(null);
        link1.setType(PROFILE_OPDS_CATALOG);
        entry.setOtherLinks(List.of(link1));
        final Entry entry1 = new Entry();
        final Content contentEntry1 = new Content();
        contentEntry1.setValue("Новые поступления за неделю");
        entry1.setContents(List.of(contentEntry1));
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
        feed.setEntries(List.of(entry, entry1));
        return feed;
    }

    public Feed createAuthorIndex(String author) {
        Feed feed = getFeed();
        author = author == null ? "" : author.toUpperCase(Locale.ROOT);
        final List<Pair<String, Long>> prefixes = authorRepository.findPrefixes(author);
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
                    final Long count = i.getSecond();

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
        feed.setOtherLinks(List.of(startLink, selfLink));
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
            bio.setId("tag:author:bio:"+author.getId());
            bio.setTitle("Об авторе");
            final Content content = new Content();
            content.setType("text/html");
            content.setValue(author.getFullName()); //TODO тут должно быть чтото типа биографии
            bio.setSummary(content);

            final Entry alphabet = new Entry();
            alphabet.setId("tag:author:"+author.getId()+":alphabet");
            alphabet.setTitle("Книги по алфавиту");
            final Link alphabetLink = new Link();
            alphabetLink.setRel(null);
            alphabetLink.setHref("/opds/author/"+author.getId()+"/alphabet");
            alphabetLink.setType(PROFILE_OPDS_CATALOG);
            alphabet.setAlternateLinks(List.of(alphabetLink));
            return List.of(bio, alphabet);
        }).orElse(List.of()));
        return feed;
    }

    @Transactional
    public Feed getBooksByAlphabet(Integer authorId) {
        Feed feed = getFeed();
        final List<Book> books = bookRepository.findByAuthorsContains(authorId);
        feed.setEntries(books.stream().map(this::createBookEntry).toList());
        return feed;
    }

    private Entry createBookEntry(Book book) {
        final Entry entry = new Entry();
        entry.setTitle(book.getTitle());
        entry.setId("tag:book:"+book.getId());
        entry.setAuthors(book.getAuthors().stream().map(author -> {
            SyndPerson person = new Person();
            person.setName(author.getFullName());
            person.setUri("/a/" + author.getId().toString());
            return person;
        }).toList());
        entry.setCategories(book.getGenres().stream().map(genre -> {
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
        entry.setAlternateLinks(List.of(downloadLink));
        return entry;
    }
}
