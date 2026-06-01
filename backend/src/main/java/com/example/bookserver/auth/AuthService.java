package com.example.bookserver.auth;

import com.example.bookserver.auth.dto.LoginRequest;
import com.example.bookserver.auth.dto.MeResponse;
import com.example.bookserver.auth.dto.RefreshRequest;
import com.example.bookserver.auth.dto.RegisterRequest;
import com.example.bookserver.auth.dto.TokenResponse;
import com.example.bookserver.domain.RoleEntity;
import com.example.bookserver.domain.UserEntity;
import com.example.bookserver.repo.RoleRepository;
import com.example.bookserver.repo.UserRepository;
import com.example.bookserver.security.CustomUserDetailsService;
import com.example.bookserver.security.JwtService;
import com.example.bookserver.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
public class AuthService {

    public static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Transactional
    public MeResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }
        String email = (request.email() == null || request.email().isBlank()) ? null : request.email();
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use: " + email);
        }

        RoleEntity userRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role " + DEFAULT_ROLE + " is missing — Liquibase seed not applied?"));

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(new HashSet<>());
        user.getRoles().add(userRole);

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
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);
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
