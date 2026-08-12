package com.example.bookserver.history;

import com.example.bookserver.security.UserPrincipal;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
public class BookViewHistoryController {
    private final BookViewHistoryService service;
    public BookViewHistoryController(BookViewHistoryService service) { this.service = service; }
    @GetMapping public Page<BookViewHistoryService.HistoryDto> list(@AuthenticationPrincipal UserPrincipal user, Pageable pageable) { return service.list(user.getId(), pageable); }
    @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) { service.delete(user.getId(), id); }
    @DeleteMapping public void clear(@AuthenticationPrincipal UserPrincipal user) { service.clear(user.getId()); }
}
