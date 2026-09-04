package com.example.bookserver.repo;

import com.example.bookserver.domain.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByTelegramUid(Long telegramUid);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Page<UserEntity> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    @Query("select count(u) from UserEntity u join u.roles r where u.enabled = true and r.name = 'ROLE_ADMIN'")
    long countEnabledAdmins();
}
