package com.translatelab.backend.common.exception;

import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.auth.exception.InvalidCredentialsException;
import com.translatelab.backend.messaging.exception.MessagePublishingException;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.translation.exception.*;
import com.translatelab.backend.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Некорректное значение"
                ));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Ошибка валидации запроса",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Некорректное тело запроса",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(UnsupportedFileFormatException.class)
    public ResponseEntity<ApiError> handleUnsupportedFileFormat(
            UnsupportedFileFormatException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(InvalidDocumentUploadException.class)
    public ResponseEntity<ApiError> invalidDocumentUploadException(
            InvalidDocumentUploadException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> userNotFoundException(
            UserNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler({
            StorageException.class,
            MessagePublishingException.class
    })
    public ResponseEntity<ApiError> handleServiceUnavailable(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Ошибка инфраструктуры при обработке запроса {}",
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Сервис временно недоступен. Повторите попытку позже",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(TranslationJobNotFoundException.class)
    public ResponseEntity<ApiError> handleTranslationJobNotFound(
            TranslationJobNotFoundException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(TranslationResultNotReadyException.class)
    public ResponseEntity<ApiError> handleTranslationResultNotReadyException(
            TranslationResultNotReadyException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiError> handleInvalidPaginationException(
            InvalidPaginationException exception,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> fieldErrors
    ) {
        ApiError apiError = new ApiError(
                Instant.now(),
                status.value(),
                message,
                path,
                fieldErrors
        );

        return ResponseEntity
                .status(status)
                .body(apiError);
    }
}