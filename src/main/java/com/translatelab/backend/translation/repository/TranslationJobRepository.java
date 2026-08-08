package com.translatelab.backend.translation.repository;

import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from TranslationJob job
            where job.status in :statuses
              and job.updatedAt <= :cutoff
            order by job.updatedAt, job.id
            """)
    List<TranslationJob> findAbandonedForUpdate(
            @Param("statuses") List<TranslationStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from TranslationJob job
            where job.status = :status
              and job.sourceDeletedAt is null
              and job.updatedAt <= :cutoff
            order by job.updatedAt, job.id
            """)
    List<TranslationJob> findSourcesForCleanup(
            @Param("status") TranslationStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from TranslationJob job
            where job.status = com.translatelab.backend.translation.entity.TranslationStatus.DONE
              and job.resultFileKey is not null
              and job.resultDeletedAt is null
              and job.updatedAt <= :cutoff
            order by job.updatedAt, job.id
            """)
    List<TranslationJob> findResultsForCleanup(
            @Param("cutoff") Instant cutoff,
            Pageable pageable
    );

    boolean existsBySourceFileKey(String sourceFileKey);
}
