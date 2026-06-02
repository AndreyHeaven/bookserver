package com.example.bookserver.repo;

import com.example.bookserver.domain.ConversionJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, Long> {
}
