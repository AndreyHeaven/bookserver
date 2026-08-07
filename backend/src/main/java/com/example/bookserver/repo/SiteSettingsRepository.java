package com.example.bookserver.repo;

import com.example.bookserver.domain.SiteSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SiteSettingsRepository extends JpaRepository<SiteSettings, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SiteSettings s where s.id = 1")
    SiteSettings lockSingleton();
}
