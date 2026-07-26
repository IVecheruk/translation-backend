package com.translatelab.backend.translation.service;

import com.translatelab.backend.translation.dto.DocumentHistoryItemResponse;
import com.translatelab.backend.translation.dto.DocumentHistoryResponse;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.exception.InvalidPaginationException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TranslationJobRepository translationJobRepository;

    public DocumentHistoryService(
            TranslationJobRepository translationJobRepository
    ) {
        this.translationJobRepository = translationJobRepository;
    }

    @Transactional(readOnly = true)
    public DocumentHistoryResponse getHistory(
            UUID userId,
            int page,
            int size
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        validatePagination(page, size);

        Pageable pageable = PageRequest.of(page, size);

        Page<TranslationJob> jobsPage = translationJobRepository
                .findAllByUser_IdOrderByCreatedAtDesc(
                        userId,
                        pageable
                );

        List<DocumentHistoryItemResponse> items = jobsPage
                .getContent()
                .stream()
                .map(this::toHistoryItem)
                .toList();

        return new DocumentHistoryResponse(
                items,
                jobsPage.getNumber(),
                jobsPage.getSize(),
                jobsPage.getTotalElements(),
                jobsPage.getTotalPages(),
                jobsPage.isFirst(),
                jobsPage.isLast()
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException(
                    "Номер страницы не должен быть отрицательным"
            );
        }

        if (size <= 0) {
            throw new InvalidPaginationException(
                    "Размер страницы должен быть положительным"
            );
        }

        if (size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "Размер страницы не должен превышать "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private DocumentHistoryItemResponse toHistoryItem(
            TranslationJob job
    ) {
        return new DocumentHistoryItemResponse(
                job.getId(),
                job.getSourceLang(),
                job.getTargetLang(),
                job.getFileFormat(),
                job.getStatus(),
                job.getProgress(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getErrorMessage()
        );
    }
}