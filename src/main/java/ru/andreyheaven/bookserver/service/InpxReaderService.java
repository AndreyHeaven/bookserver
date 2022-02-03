package ru.andreyheaven.bookserver.service;

import org.slf4j.*;
import org.springframework.stereotype.*;
import org.springframework.util.*;
import ru.andreyheaven.bookserver.config.*;
import ru.andreyheaven.bookserver.domain.*;
import ru.andreyheaven.bookserver.repository.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
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
    private Map<String, Author> cacheAuthors;
    private Map<String, Genre> cacheGenres = new ConcurrentHashMap<>();
    private Set<Integer> savedBooksIds;

    public InpxReaderService(AuthorRepository authorRepository, BookRepository bookRepository, GenreRepository genreRepository, AppProperties properties) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.properties = properties;
    }

    public void readFile() throws IOException {
        log.info("Start reload INPX");
        initCache();
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
                            final Predicate<ZipEntry> isInProps = zipEntry -> {
                                final String name = zipEntry.getName();
                                if ((properties.getFiles() != null) && ((properties.getFiles().getInclude() != null && !properties.getFiles().getInclude().isEmpty()
                                                                         && properties.getFiles().getInclude().stream().noneMatch(s -> pathMatcher.match(s, name)))
                                                                        || (properties.getFiles().getExclude() != null && !properties.getFiles().getExclude().isEmpty()
                                                                            && properties.getFiles().getExclude().stream().anyMatch(s -> pathMatcher.match(s, name))))) {
                                    return false;
                                } else
                                    return true;
                            };
                            final Predicate<ZipEntry> isInp = zipEntry -> zipEntry.getName().endsWith(".inp");
                            zipFile.stream().filter(isInp.and(isInProps))
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
                                    .forEach(bookRepository::save);


                        } catch (IOException e) {
                            log.error("Проблемы с открытием INPX", e);

                        }

                    });

        } catch (Throwable t) {
            log.error("Проверьте папку с книгами", t);
        } finally {
            log.info("Finish reload INPX");
            clearCache();
        }
//        } catch (IOException e) {
//            log.error("Проверьте папку с книгами", e);
//        }
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
        final String[] rawFields = s.split("\u0004", 16);
        if (rawFields[8].equals("1")) return null;
        LocalDate pubDate;
        try {
            pubDate = LocalDate.parse(rawFields[10], dateTimeFormatter);
        } catch (Exception e) {
            pubDate = null;
        }
        final String s1 = rawFields[1];
        final List<String> rawGenres = Arrays.asList(s1.split(":"));
        final int id = Integer.parseInt(rawFields[5]);
        if (savedBooksIds.contains(id)) {
            log.debug("\tКнига с id %d уже есть".formatted(id));
            return null;
        }
        //TODO тут лучше использовать бинарный поиск на отсортированных массивах пропертей
        if ((rawGenres.size() > 0 && properties.getGenres() != null) && ((properties.getGenres().getInclude() != null && !properties.getGenres().getInclude().isEmpty()
                                                                          && properties.getGenres().getInclude().stream().noneMatch(rawGenres::contains)) || (properties.getGenres().getExclude() != null && !properties.getGenres().getExclude().isEmpty()
                                                                                                                                                              && properties.getGenres().getExclude().stream().anyMatch(rawGenres::contains)))) {
            return null;
        }
        return new Book(getAuthors(rawFields[0]), getGenres(s1), rawFields[2], rawFields[3],
                rawFields[4], id, Integer.parseInt(rawFields[6]),
                rawFields[7], Boolean.parseBoolean(rawFields[8]), rawFields[9], pubDate,
                rawFields[12], rawFields[13]);
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
        return Arrays.stream(s.split(":")).map(s1 -> cacheGenres.computeIfAbsent(s1, s2 -> {
            log.warn("No genre with code '{}' save it", s2);
            return genreRepository.save(new Genre(s2, s2));
        })).toList();
    }

    private Collection<Author> getAuthors(String s) {
        return Arrays.stream(s.split(":")).map(s1 -> cacheAuthors.computeIfAbsent(s1.trim(), s2 -> {
            final Author author = Author.fromString(s2);
            return authorRepository.save(Objects.requireNonNull(author));
        })).toList();
    }
}
