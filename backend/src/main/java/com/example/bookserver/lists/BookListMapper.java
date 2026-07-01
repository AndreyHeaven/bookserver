package com.example.bookserver.lists;

import com.example.bookserver.books.mapper.BookMapper;
import com.example.bookserver.domain.BookList;
import com.example.bookserver.domain.BookListItem;
import com.example.bookserver.lists.dto.BookListDto;
import com.example.bookserver.lists.dto.BookListItemDto;
import com.example.bookserver.lists.dto.PublicBookListDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class BookListMapper {

    private final BookMapper bookMapper;

    public BookListMapper(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    public BookListDto toDto(BookList list) {
        return new BookListDto(
                list.getId(),
                list.getTitle(),
                list.getDescription(),
                list.getCreatedAt(),
                items(list));
    }

    public PublicBookListDto toPublicDto(BookList list) {
        return new PublicBookListDto(list.getTitle(), list.getDescription(), items(list));
    }

    private List<BookListItemDto> items(BookList list) {
        return list.getItems().stream()
                .sorted(Comparator.comparingInt(BookListItem::getPosition))
                .map(item -> new BookListItemDto(
                        item.getBook().getId(),
                        item.getBook().getTitle(),
                        bookMapper.authorBriefs(item.getBook()),
                        item.getBook().getYear(),
                        item.getPosition()))
                .toList();
    }
}
