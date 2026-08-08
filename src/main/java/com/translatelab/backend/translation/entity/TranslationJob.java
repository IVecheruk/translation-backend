package com.translatelab.backend.translation.entity;

import com.translatelab.backend.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "translation_jobs")
public class TranslationJob {

    public static final int MAX_ERROR_DETAIL_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_file_key", nullable = false, length = 1024)
    private String sourceFileKey;

    @Column(name = "result_file_key", nullable = true, length = 1024)
    private String resultFileKey;

    @Column(name = "expected_result_file_key", nullable = false, length = 1024)
    private String expectedResultFileKey;

    @Column(name = "source_lang", nullable = false, length = 16)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 16)
    private String targetLang;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TranslationStatus status;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "file_format", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private FileFormat fileFormat;

    @Column(name = "created_at", updatable = false, nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "error_code", length = 64)
    @Enumerated(EnumType.STRING)
    private TranslationErrorCode errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorDetail;

    @Column(name = "source_deleted_at")
    private Instant sourceDeletedAt;

    @Column(name = "result_deleted_at")
    private Instant resultDeletedAt;

    protected TranslationJob(){}

    public TranslationJob(
        User user,
        String sourceFileKey,
        String expectedResultFileKey,
        String sourceLang,
        String targetLang,
        FileFormat fileFormat
    ) {
        this.user = user;
        this.sourceFileKey = sourceFileKey;
        this.expectedResultFileKey = requireStorageKey(
                expectedResultFileKey,
                "Ожидаемый ключ результата"
        );
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.status = TranslationStatus.PENDING;
        this.progress = 0;
        this.fileFormat = fileFormat;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getSourceFileKey() {
        return sourceFileKey;
    }

    public String getResultFileKey() {
        return resultFileKey;
    }

    public String getExpectedResultFileKey() {
        return expectedResultFileKey;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public TranslationStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public FileFormat getFileFormat() {
        return fileFormat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public TranslationErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public String getPublicErrorMessage() {
        return errorCode == null ? null : errorCode.publicMessage();
    }

    public Instant getSourceDeletedAt() {
        return sourceDeletedAt;
    }

    public Instant getResultDeletedAt() {
        return resultDeletedAt;
    }

    public void markSourceDeleted(Instant deletedAt) {
        if (status != TranslationStatus.DONE
                && status != TranslationStatus.FAILED) {
            throw new IllegalStateException(
                    "Исходный файл активного задания удалять нельзя"
            );
        }

        if (deletedAt == null) {
            throw new IllegalArgumentException(
                    "Время удаления исходного файла не должно быть null"
            );
        }

        if (sourceDeletedAt == null) {
            sourceDeletedAt = deletedAt;
        }
    }

    public void markResultDeleted(Instant deletedAt) {
        if (status != TranslationStatus.DONE
                || resultFileKey == null
                || resultFileKey.isBlank()) {
            throw new IllegalStateException(
                    "Удалить результат можно только у завершенного задания"
            );
        }

        if (deletedAt == null) {
            throw new IllegalArgumentException(
                    "Время удаления результата не должно быть null"
            );
        }

        if (resultDeletedAt == null) {
            resultDeletedAt = deletedAt;
        }
    }

    public void startProcessing() {
        if (this.status != TranslationStatus.PENDING) {
            throw new IllegalStateException(
              "Начать обработку можно только для задания со статусом PENDING"
            );
        }

        this.status = TranslationStatus.PROCESSING;
        this.progress = 0;
    }

    public void complete() {
        if (this.status != TranslationStatus.PROCESSING) {
            throw new IllegalStateException(
              "Завершить можно только задание со статусом PROCESSING"
            );
        }

        this.resultFileKey = expectedResultFileKey;
        this.errorCode = null;
        this.errorDetail = null;
        this.progress = 100;
        this.status = TranslationStatus.DONE;
    }

    public void fail(String errorDetail) {
        if (this.status != TranslationStatus.PENDING
                && this.status != TranslationStatus.PROCESSING) {
            throw new IllegalStateException(
              "Ошибка допустима только для задания со статусом PENDING или PROCESSING"
            );
        }

        if (errorDetail == null || errorDetail.isBlank()) {
            throw new IllegalArgumentException(
              "Сообщение об ошибке не должно быть пустым"
            );
        }

        String normalizedErrorDetail = errorDetail.strip();
        if (normalizedErrorDetail.length() > MAX_ERROR_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Диагностическое сообщение не должно превышать "
                            + MAX_ERROR_DETAIL_LENGTH
                            + " символов"
            );
        }

        this.resultFileKey = null;
        this.errorCode = TranslationErrorCode.TRANSLATION_FAILED;
        this.errorDetail = normalizedErrorDetail;
        this.status = TranslationStatus.FAILED;
    }

    public void updateProgress(int progress) {
        if (this.status != TranslationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Обновлять прогресс можно только для задания "
                        + "со статусом PROCESSING"
            );
        }

        if (progress < 0 || progress > 99) {
            throw new IllegalArgumentException(
                    "Прогресс обработки должен находиться "
                    + "в диапазоне от 0 до 99"
            );
        }

        if (progress < this.progress) {
            throw new IllegalArgumentException(
                    "Прогресс обработки не должен уменьшаться"
            );
        }

        this.progress = progress;
    }

    private String requireStorageKey(String key, String fieldName) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " не должен быть пустым"
            );
        }
        if (key.length() > 1024) {
            throw new IllegalArgumentException(
                    fieldName + " не должен превышать 1024 символа"
            );
        }
        return key;
    }
}
