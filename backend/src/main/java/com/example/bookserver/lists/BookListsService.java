package com.example.bookserver.lists;

import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookList;
import com.example.bookserver.domain.BookListItem;
import com.example.bookserver.lists.dto.AddBookToListRequest;
import com.example.bookserver.lists.dto.BookListDto;
import com.example.bookserver.lists.dto.CreateBookListRequest;
import com.example.bookserver.lists.dto.UpdateBookListRequest;
import com.example.bookserver.repo.BookListRepository;
import com.example.bookserver.repo.BookRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class BookListsService {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;
    private final ListAccessGuard guard;
    private final BookListMapper mapper;

    public BookListsService(BookListRepository bookListRepository,
                            BookRepository bookRepository,
                            ListAccessGuard guard,
                            BookListMapper mapper) {
        this.bookListRepository = bookListRepository;
        this.bookRepository = bookRepository;
        this.guard = guard;
        this.mapper = mapper;
    }

    @Transactional
    public BookListDto create(CreateBookListRequest request) {
        BookList list = new BookList();
        list.setOwner(guard.currentUser());
        list.setTitle(request.title());
        list.setDescription(request.description());
        return mapper.toDto(bookListRepository.save(list));
    }

    @Transactional(readOnly = true)
    public Page<BookListDto> listMine(Pageable pageable) {
        return bookListRepository.findByOwnerId(guard.currentUserId(), pageable).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public BookListDto get(Long id) {
        return mapper.toDto(guard.requireOwnedList(id));
    }

    @Transactional
    public BookListDto update(Long id, UpdateBookListRequest request) {
        BookList list = guard.requireOwnedList(id);
        list.setTitle(request.title());
        list.setDescription(request.description());
        return mapper.toDto(bookListRepository.save(list));
    }

    @Transactional
    public void delete(Long id) {
        bookListRepository.delete(guard.requireOwnedList(id));
    }

    @Transactional
    public BookListDto addItem(Long listId, AddBookToListRequest request) {
        BookList list = guard.requireOwnedList(listId);
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + request.bookId()));
        boolean present = list.getItems().stream()
                .anyMatch(i -> i.getBook().getId().equals(book.getId()));
        if (!present) {
            int position = request.position() != null ? request.position() : nextPosition(list);
            list.getItems().add(new BookListItem(list, book, position));
        }
        return mapper.toDto(bookListRepository.save(list));
    }

    @Transactional
    public BookListDto removeItem(Long listId, Long bookId) {
        BookList list = guard.requireOwnedList(listId);
        list.getItems().removeIf(i -> i.getBook().getId().equals(bookId));
        return mapper.toDto(bookListRepository.save(list));
    }

    @Transactional
    public BookListDto reorder(Long listId, List<Long> bookIdsInOrder) {
        BookList list = guard.requireOwnedList(listId);
        for (BookListItem item : list.getItems()) {
            int index = bookIdsInOrder.indexOf(item.getBook().getId());
            // Unlisted items are pushed to the end, preserving their relative order.
            item.setPosition(index >= 0 ? index : bookIdsInOrder.size());
        }
        return mapper.toDto(bookListRepository.save(list));
    }

    private int nextPosition(BookList list) {
        return list.getItems().stream()
                .max(Comparator.comparingInt(BookListItem::getPosition))
                .map(i -> i.getPosition() + 1)
                .orElse(0);
    }
}
