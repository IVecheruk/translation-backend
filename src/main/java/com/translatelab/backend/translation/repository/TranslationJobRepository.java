package com.translatelab.backend.translation.repository;

import com.translatelab.backend.translation.entity.TranslationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TranslationJobRepository extends JpaRepository<TranslationJob, UUID> {

    Optional<TranslationJob> findByIdAndUser_Id(
            UUID jobId,
            UUID userId
    );

    Page<TranslationJob> findAllByUser_IdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );
}
