package com.translatelab.backend.translation.scheduler;

import com.translatelab.backend.translation.service.DocumentCleanupResult;
import com.translatelab.backend.translation.service.DocumentRetentionCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentRetentionCleanupSchedulerTest {

    @Mock
    private DocumentRetentionCleanupService cleanupService;

    @Test
    void shouldDelegateOneCleanupBatch() {
        given(cleanupService.cleanupBatch())
                .willReturn(new DocumentCleanupResult(1, 2, 3, 4, 0));
        DocumentRetentionCleanupScheduler scheduler =
                new DocumentRetentionCleanupScheduler(cleanupService);

        scheduler.cleanupDocuments();

        verify(cleanupService).cleanupBatch();
    }

    @Test
    void shouldUseConfiguredDelayForInitialAndRepeatedRuns()
            throws NoSuchMethodException {
        Method method = DocumentRetentionCleanupScheduler.class
                .getMethod("cleanupDocuments");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals(
                "${app.document-retention.cleanup-interval}",
                scheduled.initialDelayString()
        );
        assertEquals(
                "${app.document-retention.cleanup-interval}",
                scheduled.fixedDelayString()
        );
    }
}
