package com.example.bookserver.admin;

import com.example.bookserver.domain.RoleEntity;
import com.example.bookserver.history.BookViewHistoryService;
import com.example.bookserver.domain.SiteSettings;
import com.example.bookserver.domain.UserEntity;
import com.example.bookserver.repo.SiteSettingsRepository;
import com.example.bookserver.repo.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AdminService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private final UserRepository userRepository;
    private final SiteSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final BookViewHistoryService historyService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminService(UserRepository userRepository, SiteSettingsRepository settingsRepository,
                        PasswordEncoder passwordEncoder, BookViewHistoryService historyService) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public Page<UserDto> users(String query, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(query == null ? "" : query.trim(), pageable)
                .map(UserDto::from);
    }

    @Transactional(readOnly = true)
    public SiteSettingsDto settings() {
        return SiteSettingsDto.from(settingsRepository.findById(SiteSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Site settings are missing")));
    }

    @Transactional
    public SiteSettingsDto updateSettings(UpdateSiteSettingsRequest request) {
        SiteSettings settings = settingsRepository.lockSingleton();
        settings.setRegistrationEnabled(request.registrationEnabled());
        settings.setHistoryRetentionDays(request.historyRetentionDays());
        settings.setHistoryMaxEntries(request.historyMaxEntries());
        historyService.applyLimits(settings);
        return SiteSettingsDto.from(settings);
    }

    @Transactional
    public UserDto setTelegramUid(Long id, Long telegramUid) {
        UserEntity user = protectedTarget(id);
        user.setTelegramUid(telegramUid);
        return UserDto.from(user);
    }

    @Transactional
    public UserDto setEnabled(Long id, boolean enabled) {
        if (!enabled) {
            settingsRepository.lockSingleton();
        }
        UserEntity user = protectedTarget(id);
        if (!enabled) {
            ensureNotLastActiveAdmin(user);
        }
        user.setEnabled(enabled);
        return UserDto.from(user);
    }

    @Transactional
    public void delete(Long id) {
        settingsRepository.lockSingleton();
        UserEntity user = protectedTarget(id);
        ensureNotLastActiveAdmin(user);
        userRepository.delete(user);
    }

    @Transactional
    public PasswordResetResponse resetPassword(Long id) {
        UserEntity user = protectedTarget(id);
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        user.setPasswordHash(passwordEncoder.encode(password));
        return new PasswordResetResponse(password);
    }

    private UserEntity protectedTarget(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.example.bookserver.security.UserPrincipal current
                && current.getId().equals(id)) {
            throw new AccessDeniedException("Administrators cannot modify their own account");
        }
        return user;
    }

    private void ensureNotLastActiveAdmin(UserEntity target) {
        if (target.isEnabled() && hasAdminRole(target) && userRepository.countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException("Cannot leave the system without an active administrator");
        }
    }

    private boolean hasAdminRole(UserEntity user) {
        return user.getRoles().stream().map(RoleEntity::getName).anyMatch(ADMIN_ROLE::equals);
    }

    public record UserDto(Long id, String username, String email, boolean enabled, Long telegramUid,
                          java.util.Set<String> roles, java.time.OffsetDateTime createdAt) {
        static UserDto from(UserEntity user) {
            return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.isEnabled(),
                    user.getTelegramUid(),
                    user.getRoles().stream().map(RoleEntity::getName).collect(java.util.stream.Collectors.toSet()),
                    user.getCreatedAt());
        }
    }
    public record UpdateTelegramUidRequest(@jakarta.validation.constraints.Positive Long telegramUid) { }
    public record SiteSettingsDto(boolean registrationEnabled, int historyRetentionDays, int historyMaxEntries) {
        static SiteSettingsDto from(SiteSettings settings) { return new SiteSettingsDto(settings.isRegistrationEnabled(), settings.getHistoryRetentionDays(), settings.getHistoryMaxEntries()); }
    }
    public record UpdateSiteSettingsRequest(boolean registrationEnabled,
                                            @jakarta.validation.constraints.Min(1) int historyRetentionDays,
                                            @jakarta.validation.constraints.Min(1) int historyMaxEntries) { }
    public record PasswordResetResponse(String password) { }
}
