package com.translatelab.backend.translation;

import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.translation.service.TranslationJobCreationService;
import com.translatelab.backend.usage.repository.FeatureUsageRecordRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = "app.messaging.outbox-publish-interval=1h")
class TranslationJobCreationRollbackIntegrationTest {

    @Autowired
    private TranslationJobCreationService creationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TranslationJobRepository jobRepository;
    @Autowired
    private FeatureUsageRecordRepository usageRepository;

    @MockitoBean
    private TranslationOutboxEventRepository outboxRepository;

    private UUID userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void shouldRollbackJobAndQuotaWhenOutboxPersistenceFails() {
        User user = userRepository.save(new User(
                "outbox-rollback-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
        userId = user.getId();
        long jobsBefore = jobRepository.count();
        long usageBefore = usageRepository.count();
        doThrow(new RuntimeException("outbox unavailable"))
                .when(outboxRepository)
                .save(any(TranslationOutboxEvent.class));

        assertThrows(
                RuntimeException.class,
                () -> creationService.create(
                        userId,
                        "uploads/user/source.docx",
                        "results/user/result.docx",
                        "en",
                        "ru",
                        FileFormat.DOCX
                )
        );

        assertEquals(jobsBefore, jobRepository.count());
        assertEquals(usageBefore, usageRepository.count());
    }
}
