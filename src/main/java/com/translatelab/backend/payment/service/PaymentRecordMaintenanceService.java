package com.translatelab.backend.payment.service;

import com.translatelab.backend.config.PaymentMaintenanceProperties;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentRecordMaintenanceService {

    private final SubscriptionPurchaseIntentRepository intentRepository;
    private final ProcessedPaymentEventRepository eventRepository;
    private final PaymentMaintenanceProperties properties;
    private final Clock clock;

    public PaymentRecordMaintenanceService(
            SubscriptionPurchaseIntentRepository intentRepository,
            ProcessedPaymentEventRepository eventRepository,
            PaymentMaintenanceProperties properties,
            Clock clock
    ) {
        this.intentRepository = intentRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceResult maintainBatch() {
        Instant now = clock.instant();
        int batchSize = properties.batchSize();
        List<SubscriptionPurchaseIntent> expired = intentRepository
                .findExpiredPendingForUpdate(
                        now,
                        PageRequest.of(0, batchSize)
                );
        expired.forEach(intent -> intent.expire(now));

        int deletedIntents = intentRepository.deleteTerminalBefore(
                now.minus(properties.terminalIntentRetention()),
                batchSize
        );
        int deletedEvents = eventRepository.deleteProcessedBefore(
                now.minus(properties.processedEventRetention()),
                batchSize
        );
        return new MaintenanceResult(
                expired.size(),
                deletedIntents,
                deletedEvents
        );
    }

    public record MaintenanceResult(
            int expiredIntents,
            int deletedIntents,
            int deletedEvents
    ) {}
}
