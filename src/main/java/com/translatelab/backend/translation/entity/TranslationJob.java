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
}