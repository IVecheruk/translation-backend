package com.translatelab.backend.payment.scheduler;

import com.translatelab.backend.config.PaymentMaintenanceProperties;
import com.translatelab.backend.payment.service.PaymentRecordMaintenanceService;
import com.translatelab.backend.payment.service.SubscriptionExpirationReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentMaintenanceScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            PaymentMaintenanceScheduler.class
    );

    private final SubscriptionExpirationReconciliationService reconciliationService;
    private final PaymentRecordMaintenanceService maintenanceService;
    private final PaymentMaintenanceProperties properties;

    public PaymentMaintenanceScheduler(
            SubscriptionExpirationReconciliationService reconciliationService,
            PaymentRecordMaintenanceService maintenanceService,
            PaymentMaintenanceProperties properties
    ) {
        this.reconciliationService = reconciliationService;
        this.maintenanceService = maintenanceService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString =
            "${app.payment-maintenance.reconciliation-interval:5m}")
    public void reconcileExpiredSubscriptions() {
        int reconciled = reconciliationService.reconcileBatch(
                properties.batchSize()
        );
        if (reconciled > 0) {
            LOGGER.info(
                    "Reconciled {} expired payment subscriptions",
                    reconciled
            );
        }
    }

    @Scheduled(fixedDelayString =
            "${app.payment-maintenance.cleanup-interval:1h}")
    public void maintainPaymentRecords() {
        PaymentRecordMaintenanceService.MaintenanceResult result =
                maintenanceService.maintainBatch();
        if (result.expiredIntents() > 0
                || result.deletedIntents() > 0
                || result.deletedEvents() > 0) {
            LOGGER.info(
                    "Payment maintenance completed: expiredIntents={}, "
                            + "deletedIntents={}, deletedEvents={}",
                    result.expiredIntents(),
                    result.deletedIntents(),
                    result.deletedEvents()
            );
        }
    }
}
