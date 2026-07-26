package com.translatelab.backend.translation.service;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TranslationStatusUpdateService {

    private final TranslationJobRepository translationJobRepository;

    public TranslationStatusUpdateService(
            TranslationJobRepository translationJobRepository
    ) {
        this.translationJobRepository = translationJobRepository;
    }

    @Transactional
    public void updateStatus(TranslationStatusMessage message) {
        validateCommonFields(message);

        TranslationJob job = translationJobRepository
                .findById(message.jobId())
                .orElseThrow(TranslationJobNotFoundException::new);

        switch (message.status()) {
            case PENDING -> throw invalid(
                    "ML-сервис не может устанавливать статус PENDING"
            );
            case PROCESSING -> process(job, message);
            case DONE -> complete(job, message);
            case FAILED -> fail(job, message);
        }
    }

    private void process(
            TranslationJob job,
            TranslationStatusMessage message
    ) {
        validateProgress(message.progress(), 99);
        requireAbsent(
                message.resultFileKey(),
                "result_file_key должен отсутствовать для статуса PROCESSING"
        );
        requireAbsent(
                message.errorMessage(),
                "error_message должен отсутствовать для статуса PROCESSING"
        );

        if (isTerminal(job.getStatus())) {
            return;
        }

        if (job.getStatus() == TranslationStatus.PENDING) {
            job.startProcessing();
        }

        if (message.progress() > job.getProgress()) {
            job.updateProgress(message.progress());
        }
    }

    private void complete(
            TranslationJob job,
            TranslationStatusMessage message
    ) {
        if (message.progress() != 100) {
            throw invalid("Для статуса DONE progress должен быть равен 100");
        }
        requirePresent(
                message.resultFileKey(),
                "Для статуса DONE требуется result_file_key"
        );
        if (message.resultFileKey().length() > 1024) {
            throw invalid(
                    "result_file_key не должен превышать 1024 символа"
            );
        }
        requireAbsent(
                message.errorMessage(),
                "error_message должен отсутствовать для статуса DONE"
        );

        if (job.getStatus() == TranslationStatus.DONE) {
            if (!job.getResultFileKey().equals(message.resultFileKey())) {
                throw invalid(
                        "Повторное сообщение DONE содержит другой result_file_key"
                );
            }
            return;
        }

        if (job.getStatus() == TranslationStatus.FAILED) {
            throw invalid(
                    "Нельзя изменить завершённое с ошибкой задание на DONE"
            );
        }

        if (job.getStatus() == TranslationStatus.PENDING) {
            job.startProcessing();
        }

        job.complete(message.resultFileKey());
    }

    private void fail(
            TranslationJob job,
            TranslationStatusMessage message
    ) {
        validateProgress(message.progress(), 99);
        requireAbsent(
                message.resultFileKey(),
                "result_file_key должен отсутствовать для статуса FAILED"
        );
        requirePresent(
                message.errorMessage(),
                "Для статуса FAILED требуется error_message"
        );

        if (job.getStatus() == TranslationStatus.FAILED) {
            return;
        }

        if (job.getStatus() == TranslationStatus.DONE) {
            throw invalid(
                    "Нельзя изменить успешно завершённое задание на FAILED"
            );
        }

        if (job.getStatus() == TranslationStatus.PENDING
                && message.progress() > 0) {
            job.startProcessing();
        }

        if (job.getStatus() == TranslationStatus.PROCESSING
                && message.progress() > job.getProgress()) {
            job.updateProgress(message.progress());
        }

        job.fail(message.errorMessage());
    }

    private void validateCommonFields(TranslationStatusMessage message) {
        if (message == null) {
            throw invalid("Сообщение статуса не должно быть null");
        }
        if (message.jobId() == null) {
            throw invalid("job_id не должен быть null");
        }
        if (message.status() == null) {
            throw invalid("status не должен быть null");
        }
    }

    private void validateProgress(int progress, int maximum) {
        if (progress < 0 || progress > maximum) {
            throw invalid(
                    "progress должен находиться в диапазоне от 0 до "
                            + maximum
            );
        }
    }

    private void requirePresent(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private void requireAbsent(String value, String message) {
        if (value != null) {
            throw invalid(message);
        }
    }

    private boolean isTerminal(TranslationStatus status) {
        return status == TranslationStatus.DONE
                || status == TranslationStatus.FAILED;
    }

    private InvalidTranslationStatusMessageException invalid(
            String message
    ) {
        return new InvalidTranslationStatusMessageException(message);
    }
}
