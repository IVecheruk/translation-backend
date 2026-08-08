package com.translatelab.backend.translation.validation;

import com.translatelab.backend.translation.entity.FileFormat;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFormatValidator {

    FileFormat supportedFormat();

    void validate(MultipartFile file);
}