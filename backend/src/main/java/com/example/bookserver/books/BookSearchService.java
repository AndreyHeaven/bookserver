package com.example.bookserver.books;

import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookSearchRequest;
import com.example.bookserver.books.dto.BookSearchResponse;
import com.example.bookserver.books.dto.FacetBucketDto;
import com.example.bookserver.books.dto.FacetCountsDto;
import com.example.bookserver.books.dto.GenreFacetBucketDto;
import com.example.bookserver.books.mapper.BookMapper;
import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.Genre;
import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.repo.BookSearchProjection;
import com.example.bookserver.repo.FacetCounts;
import com.example.bookserver.repo.FacetFilter;
import com.example.bookserver.repo.GenreRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BookSearchService {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final BookMapper mapper;

    public BookSearchService(BookRepository bookRepository, GenreRepository genreRepository, BookMapper mapper) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.mapper = mapper;
    }

    public BookSearchResponse search(BookSearchRequest request) {
        Pageable pageable = pageable(request.page(), request.size(), request.sort(), defaultSearchSort(request.q()));
        FacetFilter filter = filter(request, true);
        Page<BookSearchProjection> hits = bookRepository.search(request.q(), filter, pageable);
        Page<BookCardDto> cards = toCards(hits, pageable);
        FacetCountsDto facets = toDto(bookRepository.facetCounts(request.q(), filter));
        return BookSearchResponse.of(cards, facets);
    }

    public FacetCountsDto facets(BookSearchRequest request) {
        return toDto(bookRepository.facetCounts(request.q(), filter(request, true)));
    }

    public BookDetailsDto getDetails(Long id) {
        Book book = bookRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        return mapper.toDetails(book);
    }

    public Page<BookCardDto> searchBooks(String q,
                                         String lang,
                                         Integer yearFrom,
                                         Integer yearTo,
                                         List<Long> genreIds,
                                         Long authorId,
                                         Boolean includeSubgenres,
                                         Integer page,
                                         Integer size,
                                         String sort,
                                         Sort fallbackSort) {
        BookSearchRequest request = new BookSearchRequest(q, lang, yearFrom, yearTo, genreIds, authorId, page, size, sort);
        Pageable pageable = pageable(page, size, sort, fallbackSort);
        FacetFilter filter = filter(request, includeSubgenres == null || includeSubgenres);
        return toCards(bookRepository.search(q, filter, pageable), pageable);
    }

    private Page<BookCardDto> toCards(Page<BookSearchProjection> hits, Pageable pageable) {
        if (hits.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, hits.getTotalElements());
        }
        List<Long> ids = hits.getContent().stream().map(BookSearchProjection::id).toList();
        Map<Long, Book> books = bookRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
        List<BookCardDto> cards = ids.stream()
                .map(books::get)
                .filter(java.util.Objects::nonNull)
                .map(mapper::toCard)
                .toList();
        return new PageImpl<>(cards, pageable, hits.getTotalElements());
    }

    private FacetCountsDto toDto(FacetCounts counts) {
        Map<Long, Genre> genres = genreRepository.findAllById(counts.genres().keySet()).stream()
                .collect(Collectors.toMap(Genre::getId, Function.identity()));
        List<FacetBucketDto<String>> langs = counts.langs().entrySet().stream()
                .map(e -> new FacetBucketDto<>(e.getKey(), e.getValue()))
                .toList();
        List<FacetBucketDto<Integer>> years = counts.years().entrySet().stream()
                .map(e -> new FacetBucketDto<>(e.getKey(), e.getValue()))
                .toList();
        List<GenreFacetBucketDto> genreBuckets = counts.genres().entrySet().stream()
                .map(e -> {
                    Genre g = genres.get(e.getKey());
                    return new GenreFacetBucketDto(e.getKey(),
                            g == null ? null : g.getCode(),
                            g == null ? null : g.getTitle(),
                            e.getValue());
                })
                .toList();
        return new FacetCountsDto(langs, years, genreBuckets);
    }

    private FacetFilter filter(BookSearchRequest request, boolean includeSubgenres) {
        List<Long> genreIds = request.genreId() == null ? List.of() : request.genreId();
        if (!genreIds.isEmpty() && includeSubgenres) {
            genreIds = expandGenreIds(genreIds);
        }
        return new FacetFilter(
                blankToNull(request.lang()),
                request.yearFrom(),
                request.yearTo(),
                genreIds,
                request.authorId());
    }

    private List<Long> expandGenreIds(List<Long> genreIds) {
        Set<Long> out = new LinkedHashSet<>(genreIds);
        List<Genre> all = genreRepository.findAll();
        Map<Long, List<Long>> children = all.stream()
                .filter(g -> g.getParent() != null)
                .collect(Collectors.groupingBy(g -> g.getParent().getId(),
                        Collectors.mapping(Genre::getId, Collectors.toList())));
        List<Long> queue = new ArrayList<>(genreIds);
        for (int i = 0; i < queue.size(); i++) {
            for (Long child : children.getOrDefault(queue.get(i), List.of())) {
                if (out.add(child)) {
                    queue.add(child);
                }
            }
        }
        return List.copyOf(out);
    }

    private Pageable pageable(Integer page, Integer size, String sort, Sort fallbackSort) {
        return PageRequest.of(
                page == null ? 0 : page,
                Math.min(size == null ? 20 : size, 100),
                parseSort(sort, fallbackSort));
    }

    private Sort parseSort(String sort, Sort fallbackSort) {
        if (sort == null || sort.isBlank()) {
            return fallbackSort;
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!List.of("title", "year", "lang", "id", "series", "sequenceNumber").contains(property)) {
            return fallbackSort;
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private static Sort defaultSearchSort(String q) {
        return q == null || q.isBlank() ? Sort.by("title").ascending() : Sort.unsorted();
    }

    public static Sort authorBooksSort() {
        return Sort.by("series").ascending()
                .and(Sort.by("sequenceNumber").ascending())
                .and(Sort.by("title").ascending());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
