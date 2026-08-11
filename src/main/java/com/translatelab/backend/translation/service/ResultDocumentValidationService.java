package com.translatelab.backend.translation.service;

import com.translatelab.backend.config.DocumentUploadProperties;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import com.translatelab.backend.translation.validation.DocumentContentValidator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ResultDocumentValidationService {

    private static final int BUFFER_SIZE = 8192;

    private final StorageService storageService;
    private final DocumentContentValidator documentContentValidator;
    private final DocumentUploadProperties documentUploadProperties;

    public ResultDocumentValidationService(
            StorageService storageService,
            DocumentContentValidator documentContentValidator,
            DocumentUploadProperties documentUploadProperties
    ) {
        this.storageService = storageService;
        this.documentContentValidator = documentContentValidator;
        this.documentUploadProperties = documentUploadProperties;
    }

    public void validate(String objectKey, FileFormat fileFormat) {
        long maximumSize = documentUploadProperties
                .maxFileSize()
                .toBytes();

        try (InputStream inputStream = storageService.download(objectKey)) {
            byte[] content = readBounded(inputStream, maximumSize);
            documentContentValidator.validate(content, fileFormat);
        } catch (IOException exception) {
            throw new StorageException(
                    "Не удалось прочитать результат перевода из MinIO",
                    exception
            );
        }
    }

    private byte[] readBounded(
            InputStream inputStream,
            long maximumSize
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            total += bytesRead;
            if (total > maximumSize) {
                throw new InvalidDocumentContentException();
            }
            output.write(buffer, 0, bytesRead);
        }

        return output.toByteArray();
    }
}
