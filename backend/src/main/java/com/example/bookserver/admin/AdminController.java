package com.example.bookserver.admin;

import com.example.bookserver.admin.AdminService.PasswordResetResponse;
import com.example.bookserver.admin.AdminService.SiteSettingsDto;
import com.example.bookserver.admin.AdminService.UpdateSiteSettingsRequest;
import com.example.bookserver.admin.AdminService.UpdateTelegramUidRequest;
import com.example.bookserver.admin.AdminService.UserDto;
import com.example.bookserver.history.BookViewHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AdminService service;
    private final BookViewHistoryService historyService;

    public AdminController(AdminService service, BookViewHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @GetMapping("/users")
    public Page<UserDto> users(@RequestParam(required = false) String q, Pageable pageable) {
        return service.users(q, pageable);
    }

    @PatchMapping("/users/{id}/enabled")
    public UserDto setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setEnabled(id, enabled);
    }

    @PutMapping("/users/{id}/telegram-uid")
    public UserDto setTelegramUid(@PathVariable Long id,
                                  @Valid @RequestBody UpdateTelegramUidRequest request) {
        return service.setTelegramUid(id, request.telegramUid());
    }

    @DeleteMapping("/users/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/users/{id}/reset-password")
    public PasswordResetResponse resetPassword(@PathVariable Long id) { return service.resetPassword(id); }

    @GetMapping("/users/{id}/history")
    public Page<BookViewHistoryService.HistoryDto> history(@PathVariable Long id, Pageable pageable) {
        historyService.requireUser(id);
        return historyService.list(id, pageable);
    }

    @GetMapping("/site-settings")
    public SiteSettingsDto settings() { return service.settings(); }

    @PutMapping("/site-settings")
    public SiteSettingsDto updateSettings(@Valid @RequestBody UpdateSiteSettingsRequest request) {
        return service.updateSettings(request);
    }
}
