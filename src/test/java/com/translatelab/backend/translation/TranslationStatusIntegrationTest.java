package com.translatelab.backend.translation;

import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentStatusResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.translation.service.DocumentStatusService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
class TranslationStatusIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TranslationJobRepository translationJobRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessagingProperties messagingProperties;

    @Autowired
    private DocumentStatusService documentStatusService;

    @Autowired
    private StorageService storageService;

    private UUID userId;
    private UUID jobId;
    private String resultFileKey;

    @AfterEach
    void cleanUp() {
        if (resultFileKey != null) {
            storageService.delete(resultFileKey);
        }
        if (jobId != null) {
            translationJobRepository.deleteById(jobId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void shouldConsumeStatusMessagesAndExposePersistedResult()
            throws Exception {
        User user = userRepository.save(new User(
                "status-test-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
        userId = user.getId();
        resultFileKey = "results/" + userId + "/"
                + UUID.randomUUID() + ".docx";

        TranslationJob job = translationJobRepository.save(
                new TranslationJob(
                        user,
                        "uploads/" + userId + "/source.docx",
                        resultFileKey,
                        "en",
                        "ru",
                        FileFormat.DOCX
                )
        );
        jobId = job.getId();

        publish(new TranslationStatusMessage(
                jobId,
                TranslationStatus.PROCESSING,
                43,
                null,
                null
        ));
        awaitStatus(TranslationStatus.PROCESSING, 43);

        byte[] resultContent = createDocx();
        storageService.upload(
                resultFileKey,
                new ByteArrayInputStream(resultContent),
                resultContent.length,
                FileFormat.DOCX.contentType()
        );
        publish(new TranslationStatusMessage(
                jobId,
                TranslationStatus.DONE,
                100,
                resultFileKey,
                null
        ));
        TranslationJob completedJob =
                awaitStatus(TranslationStatus.DONE, 100);
        DocumentStatusResponse response =
                documentStatusService.getStatus(userId, jobId);

        assertAll(
                () -> assertEquals(
                        resultFileKey,
                        completedJob.getResultFileKey()
                ),
                () -> assertNull(completedJob.getErrorDetail()),
                () -> assertEquals(jobId, response.jobId()),
                () -> assertEquals(
                        TranslationStatus.DONE,
                        response.status()
                ),
                () -> assertEquals(100, response.progress()),
                () -> assertNull(response.errorMessage())
        );
    }

    private byte[] createDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText("Translated document");
            document.write(output);
            return output.toByteArray();
        }
    }

    private void publish(TranslationStatusMessage message) {
        rabbitTemplate.convertAndSend(
                messagingProperties.exchange(),
                messagingProperties.statusRoutingKey(),
                message
        );
    }

    private TranslationJob awaitStatus(
            TranslationStatus expectedStatus,
            int expectedProgress
    ) throws InterruptedException {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(5).toNanos();

        while (System.nanoTime() < deadline) {
            TranslationJob job = translationJobRepository
                    .findById(jobId)
                    .orElseThrow();

            if (job.getStatus() == expectedStatus
                    && job.getProgress() == expectedProgress) {
                return job;
            }

            Thread.sleep(50);
        }

        return fail(
                "Статус " + expectedStatus
                        + " с прогрессом " + expectedProgress
                        + " не был сохранён за 5 секунд"
        );
    }
}
