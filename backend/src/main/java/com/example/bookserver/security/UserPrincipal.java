package com.example.bookserver.security;

import com.example.bookserver.domain.RoleEntity;
import com.example.bookserver.domain.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter exposing {@link UserEntity} as Spring Security {@link UserDetails}.
 * Carries the entity {@code id} so downstream code (controllers, services) can
 * reference the user without re-querying the repository.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Set<String> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id,
                         String username,
                         String password,
                         boolean enabled,
                         Set<String> roleNames) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.roleNames = Set.copyOf(roleNames);
        this.authorities = roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static UserPrincipal from(UserEntity user) {
        Set<String> names = user.getRoles().stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toUnmodifiableSet());
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                names
        );
    }

    public Long getId() {
        return id;
    }

    public Set<String> getRoleNames() {
        return roleNames;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
