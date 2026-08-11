package com.translatelab.backend.common.exception;

import com.translatelab.backend.messaging.exception.MessagePublishingException;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.exception.PlanPaymentOfferNotFoundException;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookException;
import com.translatelab.backend.payment.provider.tribute.exception.InvalidTributeWebhookSignatureException;
import com.translatelab.backend.plan.exception.FeatureNotAvailableException;
import com.translatelab.backend.plan.exception.SubscriptionPlanNotFoundException;
import com.translatelab.backend.storage.exception.StorageException;
import com.translatelab.backend.translation.exception.DocumentTooLargeException;
import com.translatelab.backend.translation.exception.InvalidDocumentUploadException;
import com.translatelab.backend.translation.exception.InvalidPaginationException;
import com.translatelab.backend.translation.exception.TranslationJobNotFoundException;
import com.translatelab.backend.translation.exception.TranslationResultNotReadyException;
import com.translatelab.backend.translation.exception.TranslationResultExpiredException;
import com.translatelab.backend.translation.exception.UnsupportedFileFormatException;
import com.translatelab.backend.usage.exception.UsageLimitExceededException;
import com.translatelab.backend.user.exception.AvatarNotFoundException;
import com.translatelab.backend.user.exception.InvalidAvatarException;
import com.translatelab.backend.user.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        mockMvc = standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(
                        java.util.Optional.of(meterRegistry)
                ))
                .build();
    }

    @Test
    void shouldReturnBadRequestForUnsupportedFileFormat()
            throws Exception {
        mockMvc.perform(get("/test/unsupported-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Поддерживаются только файлы форматов DOCX, DOC и PDF"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/unsupported-format"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnBadRequestForInvalidDocumentUpload()
            throws Exception {
        mockMvc.perform(get("/test/invalid-document-upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Файл не должен быть пустым"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/invalid-document-upload"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnContentTooLargeForDocumentTooLarge()
            throws Exception {
        mockMvc.perform(get("/test/document-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(
                        "Размер документа превышает максимально допустимый"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/document-too-large"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnContentTooLargeForMultipartLimit()
            throws Exception {
        mockMvc.perform(get("/test/multipart-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(
                        "Размер документа превышает максимально допустимый"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/multipart-too-large"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingUser()
            throws Exception {
        mockMvc.perform(get("/test/missing-user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Пользователь не найден"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-user"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingSubscriptionPlan()
            throws Exception {
        mockMvc.perform(get("/test/missing-subscription-plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Тариф подписки не найден"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-subscription-plan"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingSubscriptionPurchaseIntent()
            throws Exception {
        mockMvc.perform(get("/test/missing-subscription-purchase-intent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Заявка на покупку подписки не найдена"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-subscription-purchase-intent"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingPlanPaymentOffer()
            throws Exception {
        mockMvc.perform(get("/test/missing-plan-payment-offer"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Платёжное предложение не найдено"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-plan-payment-offer"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnServiceUnavailableForStorageFailure()
            throws Exception {
        mockMvc.perform(get("/test/storage-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Сервис временно недоступен. "
                                + "Повторите попытку позже"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "uploads/internal-key.docx"
                                )
                        )
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/storage-failure"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnServiceUnavailableForPublishingFailure()
            throws Exception {
        mockMvc.perform(get("/test/publishing-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Сервис временно недоступен. "
                                + "Повторите попытку позже"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "RabbitMQ"
                                )
                        )
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/publishing-failure"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnSafeServiceUnavailableForPaymentProviderFailure()
            throws Exception {
        mockMvc.perform(get("/test/payment-provider-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Платёжный сервис временно недоступен"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "secret-token"
                                )
                        )
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "provider.internal"
                                )
                        )
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/payment-provider-failure"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnSafeBadRequestForInvalidTributeWebhook()
            throws Exception {
        mockMvc.perform(get("/test/invalid-tribute-webhook"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Некорректные данные webhook Tribute"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "private@example.test"
                                )
                        )
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "synthetic-payment-token"
                                )
                        )
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/invalid-tribute-webhook"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnUnauthorizedForInvalidTributeWebhookSignature()
            throws Exception {
        mockMvc.perform(get(
                        "/test/invalid-tribute-webhook-signature"
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Недействительная подпись webhook Tribute"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "trbt-signature"
                                )
                        )
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/invalid-tribute-webhook-signature"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingTranslationJob()
            throws Exception {
        mockMvc.perform(get("/test/missing-translation-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Задание перевода не найдено"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-translation-job"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnConflictForUnavailableTranslationResult()
            throws Exception {
        mockMvc.perform(get("/test/translation-result-not-ready"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Результат перевода недоступен для скачивания"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/translation-result-not-ready"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnGoneForExpiredTranslationResult()
            throws Exception {
        mockMvc.perform(get("/test/translation-result-expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.message").value(
                        "Срок хранения результата перевода истек"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/translation-result-expired"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnBadRequestForInvalidPagination()
            throws Exception {
        mockMvc.perform(get("/test/invalid-pagination"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Номер страницы не должен быть отрицательным"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/invalid-pagination"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnBadRequestForInvalidAvatar()
            throws Exception {
        mockMvc.perform(get("/test/invalid-avatar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Размер аватара не должен превышать 2 MiB"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/invalid-avatar"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnNotFoundForMissingAvatar()
            throws Exception {
        mockMvc.perform(get("/test/missing-avatar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Аватар не найден"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/missing-avatar"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnForbiddenForUnavailableFeature()
            throws Exception {
        mockMvc.perform(get("/test/feature-not-available"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "Функция недоступна в текущем тарифе"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/feature-not-available"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnTooManyRequestsForExceededUsageLimit()
            throws Exception {
        mockMvc.perform(get("/test/usage-limit-exceeded"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(
                        "Лимит использования функции "
                                + "на текущий период исчерпан"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/test/usage-limit-exceeded"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ));
    }

    @Test
    void shouldReturnSafeConflictForDatabaseConstraint() throws Exception {
        mockMvc.perform(get("/test/data-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Запрос конфликтует с текущим состоянием данных"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("users_email_key")
                        )
                ))
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

    @Test
    void shouldReturnRetryableResponseForDatabaseContention()
            throws Exception {
        mockMvc.perform(get("/test/database-contention"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Ресурс временно занят, повторите запрос"
                ))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.header()
                        .string("Retry-After", "1"));

        assertEquals(
                1.0,
                meterRegistry.get("translatelab.database.contention")
                        .tag("kind", "pessimistic")
                        .counter()
                        .count()
        );
    }

    @Test
    void shouldReturnSafeInternalErrorWithCorrelationId() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(
                        "Внутренняя ошибка сервера"
                ))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("secret-detail")
                        )
                ))
                .andExpect(jsonPath("$.correlation_id").isNotEmpty());
    }

    @RestController
    private static class TestController {

        @GetMapping("/test/unsupported-format")
        void throwUnsupportedFileFormatException() {
            throw new UnsupportedFileFormatException();
        }

        @GetMapping("/test/invalid-document-upload")
        void throwInvalidDocumentUploadException() {
            throw new InvalidDocumentUploadException(
                    "Файл не должен быть пустым"
            );
        }

        @GetMapping("/test/document-too-large")
        void throwDocumentTooLargeException() {
            throw new DocumentTooLargeException();
        }

        @GetMapping("/test/multipart-too-large")
        void throwMaxUploadSizeExceededException() {
            throw new MaxUploadSizeExceededException(20L);
        }

        @GetMapping("/test/missing-user")
        void throwUserNotFoundException() {
            throw new UserNotFoundException();
        }

        @GetMapping("/test/missing-subscription-plan")
        void throwSubscriptionPlanNotFoundException() {
            throw new SubscriptionPlanNotFoundException();
        }

        @GetMapping("/test/missing-subscription-purchase-intent")
        void throwSubscriptionPurchaseIntentNotFoundException() {
            throw new SubscriptionPurchaseIntentNotFoundException();
        }

        @GetMapping("/test/missing-plan-payment-offer")
        void throwPlanPaymentOfferNotFoundException() {
            throw new PlanPaymentOfferNotFoundException();
        }

        @GetMapping("/test/storage-failure")
        void throwStorageException() {
            throw new StorageException(
                    "Не удалось загрузить файл: "
                            + "uploads/internal-key.docx",
                    new RuntimeException("MinIO unavailable")
            );
        }

        @GetMapping("/test/publishing-failure")
        void throwMessagePublishingException() {
            throw new MessagePublishingException(
                    "Не удалось отправить задачу в RabbitMQ",
                    new RuntimeException("RabbitMQ unavailable")
            );
        }

        @GetMapping("/test/payment-provider-failure")
        void throwPaymentProviderUnavailableException() {
            throw new PaymentProviderUnavailableException(
                    new RuntimeException(
                            "https://provider.internal secret-token"
                    )
            );
        }

        @GetMapping("/test/invalid-tribute-webhook")
        void throwInvalidTributeWebhookException() {
            throw new InvalidTributeWebhookException(
                    new IllegalArgumentException(
                            "private@example.test "
                                    + "synthetic-payment-token"
                    )
            );
        }

        @GetMapping("/test/invalid-tribute-webhook-signature")
        void throwInvalidTributeWebhookSignatureException() {
            throw new InvalidTributeWebhookSignatureException();
        }

        @GetMapping("/test/missing-translation-job")
        void throwTranslationJobNotFoundException() {
            throw new TranslationJobNotFoundException();
        }

        @GetMapping("/test/translation-result-not-ready")
        void throwTranslationResultNotReadyException() {
            throw new TranslationResultNotReadyException();
        }

        @GetMapping("/test/translation-result-expired")
        void throwTranslationResultExpiredException() {
            throw new TranslationResultExpiredException();
        }

        @GetMapping("/test/invalid-pagination")
        void throwInvalidPaginationException() {
            throw new InvalidPaginationException(
                    "Номер страницы не должен быть отрицательным"
            );
        }

        @GetMapping("/test/invalid-avatar")
        void throwInvalidAvatarException() {
            throw new InvalidAvatarException(
                    "Размер аватара не должен превышать 2 MiB"
            );
        }

        @GetMapping("/test/missing-avatar")
        void throwAvatarNotFoundException() {
            throw new AvatarNotFoundException();
        }

        @GetMapping("/test/feature-not-available")
        void throwFeatureNotAvailableException() {
            throw new FeatureNotAvailableException();
        }

        @GetMapping("/test/usage-limit-exceeded")
        void throwUsageLimitExceededException() {
            throw new UsageLimitExceededException();
        }

        @GetMapping("/test/data-conflict")
        void throwDataConflict() {
            throw new DataIntegrityViolationException("users_email_key");
        }

        @GetMapping("/test/database-contention")
        void throwDatabaseContention() {
            throw new CannotAcquireLockException("private lock detail");
        }

        @GetMapping("/test/unexpected")
        void throwUnexpected() {
            throw new IllegalStateException("secret-detail");
        }
    }
}
