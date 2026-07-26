package com.translatelab.backend.translation.service;

import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentDownloadResult;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.exception.TranslationResultNotReadyException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentDownloadService {

    private final TranslationJobRepository translationJobRepository;
    private final StorageService storageService;

    public DocumentDownloadService (
            TranslationJobRepository translationJobRepository,
            StorageService storageService
    ) {
        this.translationJobRepository = translationJobRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public DocumentDownloadResult download(
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

        if (job.getStatus() != TranslationStatus.DONE) {
            throw new TranslationResultNotReadyException();
        }

        String resultFileKey = Objects.requireNonNull(
                job.getResultFileKey(),
                "Ключ результата завершенного задания не должен быть null"
        );

        InputStream inputStream = storageService.download(resultFileKey);

        String fileName = "translation-"
                + job.getId()
                + "."
                + job.getFileFormat().jsonValue();

        String contentType = resolveContentType(
                job.getFileFormat()
        );

        return new DocumentDownloadResult(
                inputStream,
                fileName,
                contentType
        );
    }

    private String resolveContentType(FileFormat format) {
        return switch (format) {
            case DOCX ->
                    "application/vnd.openxmlformats-officedocument"
                            + ".wordprocessingml.document";
            case DOC -> "application/msword";
            case PDF -> "application/pdf";
        };
    }
}