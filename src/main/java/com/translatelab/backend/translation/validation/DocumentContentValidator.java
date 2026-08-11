package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.exception.InvalidDocumentContentException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentContentValidator {

    private final Map<FileFormat, DocumentFormatValidator> validators;

    public DocumentContentValidator(
            List<DocumentFormatValidator> documentFormatValidators
    ) {
        EnumMap<FileFormat, DocumentFormatValidator> validatorsByFormat =
                new EnumMap<>(FileFormat.class);

        for (DocumentFormatValidator validator : documentFormatValidators) {
            DocumentFormatValidator previous = validatorsByFormat.put(
                    validator.supportedFormat(),
                    validator
            );

            if (previous != null) {
                throw new IllegalStateException(
                        "Для формата " + validator.supportedFormat()
                                + " зарегистрировано несколько валидаторов"
                );
            }
        }

        if (validatorsByFormat.size() != FileFormat.values().length) {
            throw new IllegalStateException(
                    "Валидаторы зарегистрированы не для всех форматов документов"
            );
        }

        this.validators = Map.copyOf(validatorsByFormat);
    }

    public void validate(MultipartFile file, FileFormat fileFormat) {
        validators.get(fileFormat).validate(file);
    }

    public void validate(byte[] content, FileFormat fileFormat) {
        if (content == null || content.length == 0 || fileFormat == null) {
            throw new InvalidDocumentContentException();
        }

        validators.get(fileFormat).validate(content);
    }
}
