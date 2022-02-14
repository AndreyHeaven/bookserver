package ru.andreyheaven.bookserver.service;

import org.slf4j.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;
import org.springframework.util.*;
import ru.andreyheaven.bookserver.config.*;
import ru.andreyheaven.bookserver.domain.*;
import ru.andreyheaven.bookserver.repository.*;
import java.io.*;
import java.nio.file.*;
import java.sql.Date;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;
import java.util.zip.*;

@Service
public class InpxReaderService {
    private static final Logger log = LoggerFactory.getLogger(InpxReaderService.class);
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final AppProperties properties;
    final Predicate<ZipEntry> zipEntryPredicate = getZipEntryPredicate();
    final Predicate<Object[]> rawInpPredicate = getRawInpPredicate();
    private final JdbcTemplate jdbcTemplate;
    private Map<String, Author> cacheAuthors;
    private Map<String, Genre> cacheGenres = new ConcurrentHashMap<>();
    private Set<Integer> savedBooksIds;

    public InpxReaderService(AuthorRepository authorRepository, BookRepository bookRepository, GenreRepository genreRepository, AppProperties properties, JdbcTemplate jdbcTemplate) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void readFile() {
        log.info("Start reload INPX");
        initCache();
        AtomicInteger bookCount = new AtomicInteger();
        final long startMillis = System.currentTimeMillis();
        if (properties.getFiles() != null) {
            if (properties.getFiles().getInclude() != null)
                log.info("AppProperties Files Include " + String.join(",", properties.getFiles().getInclude()));
            if (properties.getFiles().getExclude() != null)
                log.info("AppProperties Files Exclude " + String.join(",", properties.getFiles().getExclude()));
        }
        if (properties.getGenres() != null) {
            if (properties.getGenres().getInclude() != null)
                log.info("AppProperties Genres Include " + String.join(",", properties.getGenres().getInclude()));
            if (properties.getGenres().getInclude() != null)
                log.info("AppProperties Genres Exclude " + String.join(",", properties.getGenres().getExclude()));
        }
        try {
            Files.list(Paths.get(properties.getDataDir()))
                    .filter(file -> !Files.isDirectory(file))
                    .filter(file -> file.getFileName().toString().endsWith(".inpx"))
                    .forEach(path -> {
                        log.info("Found %s".formatted(path));

                        try (ZipFile zipFile = new ZipFile(path.toFile())) {

                            processZip3(bookCount, zipFile);


                        } catch (IOException e) {
                            log.error("Проблемы с открытием INPX", e);

                        }

                    });

        } catch (Throwable t) {
            log.error("Проверьте папку с книгами", t);
        } finally {
            log.info("Finish reload INPX in %s, total %d books".formatted(Duration.ofMillis(System.currentTimeMillis() - startMillis).toString(), bookCount.get()));

            clearCache();
        }
    }

    private void processZip3(AtomicInteger bookCount, ZipFile zipFile) {
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             PreparedStatement bookStmt = connection.prepareStatement("""
                     INSERT INTO books
                     (title, genres, authors, series_id, seq_number, isbn, file_id, archive, lang, format, pubdate, is_deleted, uncompressed_size)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                     """)) {
            connection.setAutoCommit(false);
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = entries.nextElement();
                if (zipEntryPredicate.test(zipEntry)) {

                    try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                         BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                        ChunkedStreams.chunks(bufferedReader.lines(), 1000).forEach(strings -> {
                            try {
                                for (String s : strings) {
                                    final Book book = getBook(s);
                                    if (book != null) {
                                        bookStmt.setString(1, book.getTitle());
                                        bookStmt.setArray(2, connection.createArrayOf("varchar", book.getGenres()));
                                        bookStmt.setArray(3, connection.createArrayOf("int", book.getAuthors()));
                                        bookStmt.setString(4, book.getSeriesId());
                                        bookStmt.setString(5, book.getSeqNumber());
                                        bookStmt.setString(6, book.getIsbn());
                                        bookStmt.setString(7, book.getFileID());
                                        bookStmt.setString(8, book.getArchive());
                                        bookStmt.setString(9, book.getLang());
                                        bookStmt.setString(10, book.getFormat());
                                        bookStmt.setDate(11, Date.valueOf(book.getPubdate())); //
                                        bookStmt.setBoolean(12, book.getDeleted());
                                        bookStmt.setInt(13, book.getUncompressedSize());
                                        bookStmt.addBatch();
                                        bookCount.incrementAndGet();
                                    }
                                }
                                bookStmt.executeBatch();
                                connection.commit();
                            } catch (SQLException e) {
                                log.error(e.getMessage(), e);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

            }
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processZip2(AtomicInteger bookCount, ZipFile zipFile) {
        zipFile.stream().filter(getZipEntryPredicate())
                .forEach(zipEntry -> {
                    try {
                        log.info("\tНайден %s".formatted(zipEntry.getName()));
                        final List<Book> bookStream = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)))
                                .lines()
                                .map(this::getBook)
                                .filter(Objects::nonNull)
                                .toList();
                        bookCount.addAndGet(bookStream.size());
                        bookRepository.saveAll(bookStream);

                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                });

    }

    private void processZip1(AtomicInteger bookCount, ZipFile zipFile) {
        zipFile.stream().filter(getZipEntryPredicate())
                .flatMap(zipEntry -> {
                    try {
                        log.info("\tНайден %s".formatted(zipEntry.getName()));
                        return new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry))).lines();

                    } catch (IOException e) {
                        return Stream.empty();
                    }
                })
                .parallel().map(this::getBook)
                .filter(Objects::nonNull)
                .peek(book -> bookCount.incrementAndGet())
                .forEach(bookRepository::save);
    }

    private Predicate<ZipEntry> getZipEntryPredicate() {
        final Predicate<ZipEntry> isInProps = zipEntry -> {
            final String name = zipEntry.getName();
            return (properties.getFiles() == null) || ((properties.getFiles().getInclude() == null || properties.getFiles().getInclude().isEmpty()
                                                        || properties.getFiles().getInclude().stream().anyMatch(s -> pathMatcher.match(s, name)))
                                                       && (properties.getFiles().getExclude() == null || properties.getFiles().getExclude().isEmpty()
                                                           || properties.getFiles().getExclude().stream().noneMatch(s -> pathMatcher.match(s, name))));
        };
        final Predicate<ZipEntry> isInp = zipEntry -> zipEntry.getName().endsWith(".inp");
        return isInp.and(isInProps);
    }

    private Predicate<Object[]> getRawInpPredicate() {
        return o -> {
            final List<String> rawGenres = List.of((String[]) o[1]);
            //TODO тут лучше использовать бинарный поиск на отсортированных массивах пропертей
            if ((rawGenres.size() > 0 && properties.getGenres() != null)
                && ((properties.getGenres().getInclude() != null && !properties.getGenres().getInclude().isEmpty()
                     && properties.getGenres().getInclude().stream().noneMatch(rawGenres::contains))
                    || (properties.getGenres().getExclude() != null && !properties.getGenres().getExclude().isEmpty()
                        && properties.getGenres().getExclude().stream().anyMatch(rawGenres::contains)))) {
                return false;
            }
            return properties.getLangs() == null || properties.getLangs().isEmpty() || properties.getLangs().contains(o[12]);
        };
    }

    private void clearCache() {
        cacheGenres.clear();
        cacheAuthors.clear();
        savedBooksIds.clear();
        cacheAuthors = null;
        cacheGenres = null;
        savedBooksIds = null;
    }

    private Book getBook(String s) {
        final Object[] rawFields = getRawData(s);
        if (rawFields == null)
            return null;
        if (savedBooksIds.contains((Integer) rawFields[5])) {
            log.debug("\tКнига с id %d уже есть".formatted((Integer) rawFields[5]));
            return null;
        }
        if (!rawInpPredicate.test(rawFields))
            return null;
        return new Book(getAuthors((String[]) rawFields[0]), getGenres((String[]) rawFields[1]), (String) rawFields[2], (String) rawFields[3],
                (String) rawFields[4], (Integer) rawFields[5], (Integer) rawFields[6],
                (String) rawFields[7], (Boolean) rawFields[8], (String) rawFields[9], (LocalDate) rawFields[10],
                (String) rawFields[11], (String) rawFields[12]);
    }

    /**
     * @param str
     * @return {String[] author, String[] genre, String title, String seriesId,
     * String seqNumber, Integer id, Integer uncompressedSize, String fileID, Boolean isDeleted,
     * String format, LocalDate pubdate, String archive, String lang}
     */
    private Object[] getRawData(String str) {
        final String[] rawFields = str.split("\u0004", 16);
        if (rawFields[8].equals("1")) return null;
        LocalDate pubDate;
        try {
            pubDate = LocalDate.parse(rawFields[10], dateTimeFormatter);
        } catch (Exception e) {
            pubDate = null;
        }
        final int id = 0;//Integer.parseInt(rawFields[5]);
        return new Object[]{rawFields[0].split(":"), rawFields[1].split(":"), rawFields[2], rawFields[3],
                rawFields[4], id, Integer.parseInt(rawFields[6]),
                rawFields[7], Boolean.parseBoolean(rawFields[8]), rawFields[9], pubDate,
                rawFields[12], rawFields[13]};
    }

    private void initCache() {
        cacheAuthors = new ConcurrentHashMap<>();
        cacheGenres = new ConcurrentHashMap<>();
        savedBooksIds = new HashSet<>();
        authorRepository.findAll().forEach(author -> cacheAuthors.put(author.getInpCode(), author));
        genreRepository.findAll().forEach(genre -> cacheGenres.put(genre.getCode(), genre));
        savedBooksIds = bookRepository.findAllIds();
    }

    private Collection<Genre> getGenres(String s) {
        return getGenres(s.split(":"));
    }

    private Collection<Genre> getGenres(String[] s) {
        return Arrays.stream(s).map(s1 -> cacheGenres.computeIfAbsent(s1, s2 -> {
            log.warn("No genre with code '{}' save it", s2);
            return genreRepository.save(new Genre(s2, s2));
        })).toList();
    }

    private Collection<Author> getAuthors(String s) {
        return getAuthors(s.split(":"));
    }

    private Collection<Author> getAuthors(String[] s) {
        return Arrays.stream(s).map(s1 -> cacheAuthors.computeIfAbsent(s1.trim(), s2 -> {
            final Author author = Author.fromString(s2);
            return authorRepository.save(Objects.requireNonNull(author));
        })).toList();
    }
}
