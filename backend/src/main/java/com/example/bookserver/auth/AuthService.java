package com.example.bookserver.auth;

import com.example.bookserver.auth.dto.LoginRequest;
import com.example.bookserver.auth.dto.MeResponse;
import com.example.bookserver.auth.dto.RefreshRequest;
import com.example.bookserver.auth.dto.RegisterRequest;
import com.example.bookserver.auth.dto.TokenResponse;
import com.example.bookserver.domain.RoleEntity;
import com.example.bookserver.domain.UserEntity;
import com.example.bookserver.repo.RoleRepository;
import com.example.bookserver.repo.SiteSettingsRepository;
import com.example.bookserver.repo.UserRepository;
import com.example.bookserver.domain.SiteSettings;
import com.example.bookserver.security.CustomUserDetailsService;
import com.example.bookserver.security.JwtService;
import com.example.bookserver.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
public class AuthService {

    public static final String DEFAULT_ROLE = "ROLE_USER";
    public static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SiteSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       SiteSettingsRepository settingsRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public MeResponse register(RegisterRequest request) {
        SiteSettings settings = settingsRepository.lockSingleton();
        if (!settings.isRegistrationEnabled()) {
            throw new org.springframework.security.access.AccessDeniedException("Registration is disabled");
        }
        // Pre-checks raise DataIntegrityViolationException (HTTP 409 via GlobalExceptionHandler),
        // consistent with the race-condition path where the DB unique constraint fires directly.
        if (userRepository.existsByUsername(request.username())) {
            throw new DataIntegrityViolationException("Username already taken");
        }
        String email = (request.email() == null || request.email().isBlank()) ? null : request.email();
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DataIntegrityViolationException("Email already in use");
        }

        RoleEntity userRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role " + DEFAULT_ROLE + " is missing — Liquibase seed not applied?"));
        RoleEntity role = userRole;
        if (!settings.isFirstPublicRegistrationCompleted()) {
            role = roleRepository.findByName(ADMIN_ROLE)
                    .orElseThrow(() -> new IllegalStateException("Admin role is missing"));
            settings.setFirstPublicRegistrationCompleted(true);
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(new HashSet<>());
        user.getRoles().add(role);

        UserEntity saved = userRepository.save(user);
        return toMeResponse(UserPrincipal.from(saved), saved.getEmail());
    }

    public TokenResponse login(LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return TokenResponse.bearer(
                    jwtService.generateAccessToken(principal),
                    jwtService.generateRefreshToken(principal));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid username or password", ex);
        }
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parseAndValidate(request.refreshToken())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("Token is not a refresh token");
        }
        String username = claims.getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        try {
            new AccountStatusUserDetailsChecker().check(userDetails);
        } catch (AccountStatusException ex) {
            throw new BadCredentialsException("Invalid or expired refresh token", ex);
        }
        UserPrincipal principal = (UserPrincipal) userDetails;
        return TokenResponse.bearer(
                jwtService.generateAccessToken(principal),
                jwtService.generateRefreshToken(principal));
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BadCredentialsException("Not authenticated");
        }
        UserEntity user = userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return toMeResponse(UserPrincipal.from(user), user.getEmail());
    }

    private static MeResponse toMeResponse(UserPrincipal principal, String email) {
        return new MeResponse(
                principal.getId(),
                principal.getUsername(),
                email,
                principal.getRoleNames());
    }
}
