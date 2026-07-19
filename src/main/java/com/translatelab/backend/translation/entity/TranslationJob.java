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

    @Column(name = "source_lang", nullable = false, length = 16)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 16)
    private String targetLang;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TranslationStatus status;

    @Column(name = "file_format", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private FileFormat fileFormat;

    @Column(name = "created_at", updatable = false, nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected TranslationJob(){}

    public TranslationJob(
        User user,
        String sourceFileKey,
        String sourceLang,
        String targetLang,
        FileFormat fileFormat
    ) {
        this.user = user;
        this.sourceFileKey = sourceFileKey;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.status = TranslationStatus.PENDING;
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

    public String getSourceLang() {
        return sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public TranslationStatus getStatus() {
        return status;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void startProcessing() {
        if (this.status != TranslationStatus.PENDING) {
            throw new IllegalStateException(
              "Начать обработку можно только для задания со статусом PENDING"
            );
        }

        this.status = TranslationStatus.PROCESSING;
    }

    public void complete(String resultFileKey) {
        if (this.status != TranslationStatus.PROCESSING) {
            throw new IllegalStateException(
              "Завершить можно только задание со статусом PROCESSING"
            );
        }

        if (resultFileKey == null || resultFileKey.isBlank()) {
            throw new IllegalArgumentException(
              "Ключ результирующего файла не должен быть пустым"
            );
        }

        if (resultFileKey.length() > 1024) {
            throw new IllegalArgumentException(
              "Ключ результирующуго файла не должен превышать 1024 символа"
            );
        }

        this.resultFileKey = resultFileKey;
        this.errorMessage = null;
        this.status = TranslationStatus.DONE;
    }

    public void fail(String errorMessage) {
        if (this.status != TranslationStatus.PENDING
                && this.status != TranslationStatus.PROCESSING) {
            throw new IllegalStateException(
              "Ошибка допустима только для задания со статусом PENDING или PROCESSING"
            );
        }

        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException(
              "Сообщение об ошибке не должно быть пустым"
            );
        }

        this.resultFileKey = null;
        this.errorMessage = errorMessage.strip();
        this.status = TranslationStatus.FAILED;
    }
}