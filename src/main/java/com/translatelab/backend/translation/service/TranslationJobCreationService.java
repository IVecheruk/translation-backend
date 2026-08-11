package com.translatelab.backend.translation.service;

import com.translatelab.backend.messaging.outbox.entity.TranslationOutboxEvent;
import com.translatelab.backend.messaging.outbox.repository.TranslationOutboxEventRepository;
import com.translatelab.backend.plan.entity.FeatureCode;
import com.translatelab.backend.translation.entity.FileFormat;
import com.translatelab.backend.translation.entity.TranslationJob;
import com.translatelab.backend.translation.repository.TranslationJobRepository;
import com.translatelab.backend.usage.service.UsageLimitService;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.exception.UserNotFoundException;
import com.translatelab.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class TranslationJobCreationService {

    private final UserRepository userRepository;
    private final TranslationJobRepository jobRepository;
    private final TranslationOutboxEventRepository outboxRepository;
    private final UsageLimitService usageLimitService;
    private final Clock clock;

    public TranslationJobCreationService(
            UserRepository userRepository,
            TranslationJobRepository jobRepository,
            TranslationOutboxEventRepository outboxRepository,
            UsageLimitService usageLimitService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.usageLimitService = usageLimitService;
        this.clock = clock;
    }

    @Transactional
    public TranslationJob create(
            UUID userId,
            String sourceFileKey,
            String resultFileKey,
            String sourceLang,
            String targetLang,
            FileFormat fileFormat
    ) {
        UUID reservationId = usageLimitService.reserve(
                userId,
                FeatureCode.DOCUMENT_TRANSLATION,
                1
        );
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        TranslationJob job = jobRepository.save(new TranslationJob(
                user,
                sourceFileKey,
                resultFileKey,
                sourceLang,
                targetLang,
                fileFormat
        ));
        outboxRepository.save(new TranslationOutboxEvent(
                job,
                clock.instant()
        ));
        usageLimitService.consume(reservationId, job.getId());
        return job;
    }
}
