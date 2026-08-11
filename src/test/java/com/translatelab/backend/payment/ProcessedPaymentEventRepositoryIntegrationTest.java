package com.translatelab.backend.payment;

import com.translatelab.backend.payment.repository.ProcessedPaymentEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class ProcessedPaymentEventRepositoryIntegrationTest {

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertNewEventAndReturnOne() {
        UUID id = UUID.randomUUID();
        String externalEventId = uniqueExternalEventId();

        int inserted = processedPaymentEventRepository.insertIfAbsent(
                id,
                "TRIBUTE",
                externalEventId,
                "SUBSCRIPTION_PAID"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, provider, external_event_id, event_type, processed_at
                FROM processed_payment_events
                WHERE id = ?
                """,
                id
        );

        assertAll(
                () -> assertEquals(1, inserted),
                () -> assertEquals(id, row.get("id")),
                () -> assertEquals("TRIBUTE", row.get("provider")),
                () -> assertEquals(
                        externalEventId,
                        row.get("external_event_id")
                ),
                () -> assertEquals(
                        "SUBSCRIPTION_PAID",
                        row.get("event_type")
                ),
                () -> assertNotNull(row.get("processed_at"))
        );
    }

    @Test
    void shouldIgnoreDuplicateProviderEventAndReturnZero() {
        UUID originalId = UUID.randomUUID();
        String externalEventId = uniqueExternalEventId();

        int inserted = processedPaymentEventRepository.insertIfAbsent(
                originalId,
                "TRIBUTE",
                externalEventId,
                "SUBSCRIPTION_PAID"
        );
        int duplicate = processedPaymentEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                "TRIBUTE",
                externalEventId,
                "SUBSCRIPTION_CANCELED"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT id, event_type
                FROM processed_payment_events
                WHERE provider = ?
                  AND external_event_id = ?
                """,
                "TRIBUTE",
                externalEventId
        );

        assertAll(
                () -> assertEquals(1, inserted),
                () -> assertEquals(0, duplicate),
                () -> assertEquals(originalId, row.get("id")),
                () -> assertEquals(
                        "SUBSCRIPTION_PAID",
                        row.get("event_type")
                )
        );
    }

    @Test
    void shouldAllowSameExternalEventIdForDifferentProviders() {
        String externalEventId = uniqueExternalEventId();

        int tributeInserted = processedPaymentEventRepository.insertIfAbsent(
                UUID.randomUUID(),
                "TRIBUTE",
                externalEventId,
                "SUBSCRIPTION_PAID"
        );
        int anotherProviderInserted =
                processedPaymentEventRepository.insertIfAbsent(
                        UUID.randomUUID(),
                        "ANOTHER_PROVIDER",
                        externalEventId,
                        "SUBSCRIPTION_PAID"
                );

        Long storedEvents = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM processed_payment_events
                WHERE external_event_id = ?
                """,
                Long.class,
                externalEventId
        );

        assertAll(
                () -> assertEquals(1, tributeInserted),
                () -> assertEquals(1, anotherProviderInserted),
                () -> assertEquals(2L, storedEvents)
        );
    }

    private String uniqueExternalEventId() {
        return "event-" + UUID.randomUUID();
    }
}
