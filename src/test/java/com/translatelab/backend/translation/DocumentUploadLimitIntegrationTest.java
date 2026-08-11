package com.translatelab.backend.translation;

import com.translatelab.backend.auth.service.JwtService;
import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.messaging.dto.TranslationTaskMessage;
import com.translatelab.backend.storage.service.StorageService;
import com.translatelab.backend.translation.entity.TranslationJob;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentUploadLimitIntegrationTest {

    private static final int FREE_MONTHLY_LIMIT = 5;
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument"
                    + ".wordprocessingml.document";

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
    private JwtService jwtService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private MessagingProperties messagingProperties;

    private UUID userId;

    @BeforeEach
    void purgeTaskQueue() {
        amqpAdmin.purgeQueue(messagingProperties.queue());
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.purgeQueue(messagingProperties.queue());

        if (userId == null) {
            return;
        }

        List<TranslationJob> jobs = translationJobRepository
                .findAllByUser_IdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(0, 100)
                )
                .getContent();

        for (TranslationJob job : jobs) {
            storageService.delete(job.getSourceFileKey());
        }

        translationJobRepository.deleteAll(jobs);
        userRepository.deleteById(userId);
    }

    @Test
    void shouldAcceptFiveUploadsAndRejectSixthBeforeSideEffects()
            throws Exception {
        User user = new User(
                "usage-limit-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        );
        user.verifyEmail(Instant.now());
        user = userRepository.save(user);
        userId = user.getId();
        String accessToken = jwtService.generateAccessToken(user);

        for (int uploadNumber = 1;
             uploadNumber <= FREE_MONTHLY_LIMIT;
             uploadNumber++) {
            performUpload(accessToken, uploadNumber)
                    .andExpect(status().isAccepted())
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                    ))
                    .andExpect(jsonPath("$.job_id").isNotEmpty());
        }

        performUpload(accessToken, FREE_MONTHLY_LIMIT + 1)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(
                        "Лимит использования функции "
                                + "на текущий период исчерпан"
                ))
                .andExpect(jsonPath("$.path").value(
                        "/api/documents/upload"
                ))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/api/account/usage")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_code").value("FREE"))
                .andExpect(jsonPath("$.feature_code")
                        .value("DOCUMENT_TRANSLATION"))
                .andExpect(jsonPath("$.period_type").value("MONTH"))
                .andExpect(jsonPath("$.unlimited").value(false))
                .andExpect(jsonPath("$.limit_units")
                        .value(FREE_MONTHLY_LIMIT))
                .andExpect(jsonPath("$.used_units")
                        .value(FREE_MONTHLY_LIMIT))
                .andExpect(jsonPath("$.remaining_units").value(0))
                .andExpect(jsonPath("$.resets_at").exists());

        List<TranslationJob> jobs = translationJobRepository
                .findAllByUser_IdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(0, 10)
                )
                .getContent();
        List<FeatureUsageRecord> usageRecords = usageRecordRepository
                .findAll()
                .stream()
                .filter(record -> userId.equals(
                        record.getUser().getId()
                ))
                .toList();
        List<TranslationTaskMessage> taskMessages = receiveTasks(
                FREE_MONTHLY_LIMIT
        );

        assertEquals(FREE_MONTHLY_LIMIT, jobs.size());
        assertEquals(FREE_MONTHLY_LIMIT, usageRecords.size());
        assertTrue(usageRecords.stream().allMatch(record ->
                record.getStatus() == UsageStatus.CONSUMED
                        && record.getUnits() == 1
                        && record.getExpiresAt() == null
                        && record.getTranslationJob() != null
        ));
        assertEquals(FREE_MONTHLY_LIMIT, taskMessages.size());

        Set<UUID> persistedJobIds = new HashSet<>(
                jobs.stream().map(TranslationJob::getId).toList()
        );
        Set<UUID> usageJobIds = new HashSet<>(
                usageRecords.stream()
                        .map(FeatureUsageRecord::getTranslationJob)
                        .map(TranslationJob::getId)
                        .toList()
        );
        Set<UUID> publishedJobIds = new HashSet<>(
                taskMessages.stream()
                        .map(TranslationTaskMessage::jobId)
                        .toList()
        );

        assertFalse(persistedJobIds.contains(null));
        assertEquals(persistedJobIds, usageJobIds);
        assertEquals(persistedJobIds, publishedJobIds);
        assertNull(rabbitTemplate.receive(
                messagingProperties.queue(),
                100
        ));
    }

    private ResultActions performUpload(
            String accessToken,
            int uploadNumber
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document-" + uploadNumber + ".docx",
                DOCX_CONTENT_TYPE,
                TestDocumentFactory.docx("document " + uploadNumber)
        );

        return mockMvc.perform(
                multipart("/api/documents/upload")
                        .file(file)
                        .param("source_lang", "en")
                        .param("target_lang", "ru")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        )
        );
    }

    private List<TranslationTaskMessage> receiveTasks(
            int expectedCount
    ) throws Exception {
        List<TranslationTaskMessage> taskMessages = new ArrayList<>();

        for (int index = 0; index < expectedCount; index++) {
            Message message = rabbitTemplate.receive(
                    messagingProperties.queue(),
                    5_000
            );
            assertNotNull(message);
            taskMessages.add(objectMapper.readValue(
                    message.getBody(),
                    TranslationTaskMessage.class
            ));
        }

        return taskMessages;
    }
}
