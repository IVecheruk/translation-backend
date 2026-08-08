package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentUploadProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import com.translatelab.backend.messaging.publisher.TranslationTaskPublisher;
import com.translatelab.backend.storage.service.StorageKeyGenerator;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentUploadResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.exception.DocumentTooLargeException;
import com.translatelab.backend.translation.exception.InvalidDocumentUploadException;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.translation.validation.DocumentContentValidator;
import com.translatelab.backend.usage.service.UsageLimitService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.translatelab.backend.plan.entity.FeatureCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DocumentUploadService {

    private static final Pattern LANGUAGE_CODE_PATTERN =
            Pattern.compile("^[a-z]{2,3}$");

    private final UserRepository userRepository;
    private final TranslationJobRepository translationJobRepository;
    private final FileFormatResolver fileFormatResolver;
    private final StorageKeyGenerator storageKeyGenerator;
    private final StorageService storageService;
    private final TranslationTaskPublisher translationTaskPublisher;
    private final UsageLimitService usageLimitService;
    private final DocumentUploadProperties documentUploadProperties;
    private final DocumentContentValidator documentContentValidator;

    public DocumentUploadService(
            UserRepository userRepository,
            TranslationJobRepository translationJobRepository,
            FileFormatResolver fileFormatResolver,
            StorageKeyGenerator storageKeyGenerator,
            StorageService storageService,
            TranslationTaskPublisher translationTaskPublisher,
            UsageLimitService usageLimitService,
            DocumentUploadProperties documentUploadProperties,
            DocumentContentValidator documentContentValidator
    ) {
        this.userRepository = userRepository;
        this.translationJobRepository = translationJobRepository;
        this.fileFormatResolver = fileFormatResolver;
        this.storageKeyGenerator = storageKeyGenerator;
        this.storageService = storageService;
        this.translationTaskPublisher = translationTaskPublisher;
        this.usageLimitService = usageLimitService;
        this.documentUploadProperties = documentUploadProperties;
        this.documentContentValidator = documentContentValidator;
    }

    public DocumentUploadResponse upload(
            UUID userId,
            MultipartFile file,
            String sourceLang,
            String targetLang
    ) {
        Objects.requireNonNull(
                userId,
                "Идентификатор пользователя не должен быть null"
        );

        validateFile(file);

        String normalizedSourceLang = normalizeLanguage(
                sourceLang,
                "исходного"
        );

        String normalizedTargetLang = normalizeLanguage(
                targetLang,
                "целевого"
        );

        if (normalizedSourceLang.equals(normalizedTargetLang)) {
            throw new InvalidDocumentUploadException(
                    "Исходный и целевой языки должны различаться"
            );
        }

        FileFormat fileFormat = fileFormatResolver.resolve(
                file.getOriginalFilename()
        );

        documentContentValidator.validate(file, fileFormat);

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String objectKey = storageKeyGenerator.generateSourceFileKey(
                userId,
                fileFormat
        );
        String resultObjectKey = storageKeyGenerator.generateResultFileKey(
                userId,
                fileFormat
        );

        UUID reservationId = usageLimitService.reserve(
                userId,
                FeatureCode.DOCUMENT_TRANSLATION,
                1
        );

        TranslationJob savedJob;

        try {
            uploadFile(file, objectKey, fileFormat);

            savedJob = saveJob(
                    user,
                    objectKey,
                    resultObjectKey,
                    normalizedSourceLang,
                    normalizedTargetLang,
                    fileFormat
            );

            TranslationTaskMessage message =
                    new TranslationTaskMessage(
                            savedJob.getId(),
                            savedJob.getSourceFileKey(),
                            savedJob.getExpectedResultFileKey(),
                            savedJob.getSourceLang(),
                            savedJob.getTargetLang(),
                            savedJob.getFileFormat()
                    );

            try {
                translationTaskPublisher.publish(message);
            } catch (MessagePublishingException exception) {
                markJobAsFailed(savedJob, exception);
                throw exception;
            }
        } catch (RuntimeException exception) {
            releaseReservation(
                    reservationId,
                    exception
            );

            throw exception;
        }

        usageLimitService.consume(
                reservationId,
                savedJob.getId()
        );

        return new DocumentUploadResponse(savedJob.getId());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentUploadException(
                    "Файл не должен быть пустым"
            );
        }

        if (file.getSize() > documentUploadProperties.maxFileSize().toBytes()) {
            throw new DocumentTooLargeException();
        }
    }

    private String normalizeLanguage(
            String language,
            String languageType
    ) {
        if (language == null || language.isBlank()) {
            throw new InvalidDocumentUploadException(
                    "Код " + languageType
                            + " языка не должен быть пустым"
            );
        }

        String normalizedLanguage = language
                .strip()
                .toLowerCase(Locale.ROOT);

        if (!LANGUAGE_CODE_PATTERN
                .matcher(normalizedLanguage)
                .matches()) {
            throw new InvalidDocumentUploadException(
                    "Код " + languageType
                            + " языка должен содержать "
                            + "2 или 3 латинские буквы"
            );
        }

        return normalizedLanguage;
    }

    private void uploadFile(
            MultipartFile file,
            String objectKey,
            FileFormat fileFormat
    ) {
        boolean uploadCompleted = false;

        try (InputStream inputStream = file.getInputStream()) {
            storageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    fileFormat.contentType()
            );

            uploadCompleted = true;
        } catch (IOException exception) {
            InvalidDocumentUploadException uploadException =
                    new InvalidDocumentUploadException(
                            "Не удалось прочитать загруженный файл"
                    );

            uploadException.addSuppressed(exception);

            if (uploadCompleted) {
                deleteUploadedFile(objectKey, uploadException);
            }

            throw uploadException;
        }
    }

    private TranslationJob saveJob(
            User user,
            String objectKey,
            String resultObjectKey,
            String sourceLang,
            String targetLang,
            FileFormat fileFormat
    ) {
        try {
            TranslationJob job = new TranslationJob(
                    user,
                    objectKey,
                    resultObjectKey,
                    sourceLang,
                    targetLang,
                    fileFormat
            );

            return translationJobRepository.save(job);
        } catch (RuntimeException exception) {
            deleteUploadedFile(objectKey, exception);
            throw exception;
        }
    }

    private void deleteUploadedFile(
            String objectKey,
            RuntimeException originalException
    ) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException deleteException) {
            originalException.addSuppressed(deleteException);
        }
    }

    private void markJobAsFailed(
            TranslationJob job,
            MessagePublishingException originalException
    ) {
        try {
            job.fail(originalException.getMessage());
            translationJobRepository.save(job);
        } catch (RuntimeException updateException) {
            originalException.addSuppressed(updateException);
        }
    }

    private void releaseReservation(
            UUID reservationId,
            RuntimeException originalException
    ) {
        try {
            usageLimitService.release(reservationId);
        } catch (RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
        }
    }
}
