package com.example.bookserver.books;

import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.UpdateBookRequest;
import com.example.bookserver.books.mapper.BookMapper;
import com.example.bookserver.domain.Annotation;
import com.example.bookserver.domain.Book;
import com.example.bookserver.repo.BookRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookAdministrationService {

    private final BookRepository bookRepository;
    private final BookMapper mapper;

    public BookAdministrationService(BookRepository bookRepository, BookMapper mapper) {
        this.bookRepository = bookRepository;
        this.mapper = mapper;
    }

    @Transactional
    public BookDetailsDto update(Long id, UpdateBookRequest request) {
        Book book = bookRepository.findById(id)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + id));
        book.setTitle(request.title().trim());
        book.setLang(blankToNull(request.lang()));
        book.setYear(request.year());
        book.setKeywords(blankToNull(request.keywords()));
        String annotation = blankToNull(request.annotation());
        if (annotation == null) {
            book.setAnnotation(null);
        } else if (book.getAnnotation() == null) {
            book.setAnnotation(new Annotation(book, annotation));
        } else {
            book.getAnnotation().setBody(annotation);
        }
        return mapper.toDetails(book);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
