package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.dto.DocumentStatusResponse;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentStatusService {

    private final TranslationJobRepository translationJobRepository;

    public DocumentStatusService(
            TranslationJobRepository translationJobRepository
    ) {
        this.translationJobRepository = translationJobRepository;
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getStatus(
            UUID userId,
            UUID jobId
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        Objects.requireNonNull(
                jobId,
                "Идентификатор задания не должен быть null"
        );

        TranslationJob job = translationJobRepository
                .findByIdAndUser_Id(jobId, userId)
                .orElseThrow(TranslationJobNotFoundException::new);

        int progress = calculateProgress(job.getStatus());

        return new DocumentStatusResponse(
                job.getId(),
                job.getStatus(),
                progress,
                job.getErrorMessage()
        );
    }

    private int calculateProgress(TranslationStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case PROCESSING -> 50;
            case DONE, FAILED -> 100;
        };
    }
}