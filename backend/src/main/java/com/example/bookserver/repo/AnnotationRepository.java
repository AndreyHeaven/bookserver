package com.example.bookserver.repo;

import com.example.bookserver.domain.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
}
