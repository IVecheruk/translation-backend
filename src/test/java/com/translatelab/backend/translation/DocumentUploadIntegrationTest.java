package com.translatelab.backend.translation;

import com.translatelab.backend.auth.service.JwtService;
import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.config.StorageProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.dto.DocumentUploadResponse;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.translation.support.TestDocumentFactory;
import com.translatelab.backend.usage.entity.FeatureUsageRecord;
import com.translatelab.backend.usage.entity.UsageStatus;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TranslationJobRepository translationJobRepository;

    @Autowired
    private FeatureUsageRecordRepository usageRecordRepository;

    @Autowired
    private TranslationOutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private MessagingProperties messagingProperties;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

    private UUID userId;
    private UUID jobId;
    private String sourceFileKey;

    @BeforeEach
    void purgeTaskQueue() {
        amqpAdmin.purgeQueue(messagingProperties.queue());
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.purgeQueue(messagingProperties.queue());

        if (sourceFileKey != null) {
            storageService.delete(sourceFileKey);
        }

        if (jobId != null) {
            translationJobRepository.deleteById(jobId);
        }

        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void shouldUploadDocumentCreateTaskAndConsumeUsage() throws Exception {
        byte[] fileContent = TestDocumentFactory.docx(
                "integration document content"
        );
        User user = new User(
                "upload-test-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        );
        user.verifyEmail(Instant.now());
        user = userRepository.save(user);
        userId = user.getId();
        String accessToken = jwtService.generateAccessToken(user);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integration-document.docx",
                "application/x-msdownload",
                fileContent
        );

        MvcResult mvcResult = mockMvc.perform(
                        multipart("/api/documents/upload")
                                .file(file)
                                .param("source_lang", " EN ")
                                .param("target_lang", "RU")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.job_id").isNotEmpty())
                .andReturn();

        DocumentUploadResponse uploadResponse =
                objectMapper.readValue(
                        mvcResult.getResponse().getContentAsByteArray(),
                        DocumentUploadResponse.class
                );
        jobId = uploadResponse.jobId();

        TranslationJob savedJob = translationJobRepository
                .findByIdAndUser_Id(jobId, userId)
                .orElseThrow();
        sourceFileKey = savedJob.getSourceFileKey();

        FeatureUsageRecord usageRecord = usageRecordRepository
                .findAll()
                .stream()
                .filter(record -> record.getTranslationJob() != null)
                .filter(record -> jobId.equals(
                        record.getTranslationJob().getId()
                ))
                .findFirst()
                .orElseThrow();

        byte[] storedContent;
        try (InputStream inputStream =
                     storageService.download(sourceFileKey)) {
            storedContent = inputStream.readAllBytes();
        }

        StatObjectResponse storedMetadata = minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(storageProperties.bucket())
                        .object(sourceFileKey)
                        .build()
        );

        Message receivedMessage = rabbitTemplate.receive(
                messagingProperties.queue(),
                5_000
        );
        assertNotNull(receivedMessage);
        TranslationTaskMessage taskMessage = objectMapper.readValue(
                receivedMessage.getBody(),
                TranslationTaskMessage.class
        );
        TranslationOutboxEvent outboxEvent = outboxEventRepository
                .findByJob_Id(jobId)
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        TranslationStatus.PENDING,
                        savedJob.getStatus()
                ),
                () -> assertEquals(0, savedJob.getProgress()),
                () -> assertEquals("en", savedJob.getSourceLang()),
                () -> assertEquals("ru", savedJob.getTargetLang()),
                () -> assertEquals(
                        FileFormat.DOCX,
                        savedJob.getFileFormat()
                ),
                () -> assertNull(savedJob.getResultFileKey()),
                () -> assertTrue(
                        savedJob.getExpectedResultFileKey().startsWith(
                                "results/" + userId + "/"
                        )
                ),
                () -> assertNull(savedJob.getErrorDetail()),
                () -> assertTrue(sourceFileKey.startsWith(
                        "uploads/" + userId + "/"
                )),
                () -> assertTrue(sourceFileKey.endsWith(".docx")),
                () -> assertArrayEquals(fileContent, storedContent),
                () -> assertEquals(
                        FileFormat.DOCX.contentType(),
                        storedMetadata.contentType()
                ),
                () -> assertEquals(jobId, taskMessage.jobId()),
                () -> assertEquals(
                        outboxEvent.getId(),
                        taskMessage.eventId()
                ),
                () -> assertEquals(
                        sourceFileKey,
                        taskMessage.fileKey()
                ),
                () -> assertEquals(
                        savedJob.getExpectedResultFileKey(),
                        taskMessage.resultFileKey()
                ),
                () -> assertEquals("en", taskMessage.sourceLang()),
                () -> assertEquals("ru", taskMessage.targetLang()),
                () -> assertEquals(
                        FileFormat.DOCX,
                        taskMessage.format()
                ),
                () -> assertEquals(
                        userId,
                        usageRecord.getUser().getId()
                ),
                () -> assertEquals(
                        FeatureCode.DOCUMENT_TRANSLATION,
                        usageRecord.getFeatureCode()
                ),
                () -> assertEquals(1, usageRecord.getUnits()),
                () -> assertEquals(
                        UsageStatus.CONSUMED,
                        usageRecord.getStatus()
                ),
                () -> assertEquals(
                        jobId,
                        usageRecord.getTranslationJob().getId()
                ),
                () -> assertNull(usageRecord.getExpiresAt())
        );
    }
}
