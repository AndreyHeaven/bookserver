package com.example.bookserver.lists;

import com.example.bookserver.domain.BookList;
import com.example.bookserver.domain.UserEntity;
import com.example.bookserver.repo.BookListRepository;
import com.example.bookserver.repo.UserRepository;
import com.example.bookserver.security.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Central owner-ship resolution for book lists so private CRUD and sharing
 * enforce the same rules (404 for missing, 403 for non-owner).
 */
@Component
public class ListAccessGuard {

    private final BookListRepository bookListRepository;
    private final UserRepository userRepository;

    public ListAccessGuard(BookListRepository bookListRepository, UserRepository userRepository) {
        this.bookListRepository = bookListRepository;
        this.userRepository = userRepository;
    }

    public BookList requireOwnedList(Long id) {
        BookList list = bookListRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book list not found: " + id));
        if (!list.getOwner().getId().equals(currentUserId())) {
            throw new AccessDeniedException("You do not own this list");
        }
        return list;
    }

    public UserEntity currentUser() {
        return userRepository.findById(currentUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BadCredentialsException("Not authenticated");
        }
        return principal.getId();
    }
}
