package com.translatelab.backend.common.persistence;

import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.hikari.connection-init-sql="
                + "SET lock_timeout TO '250ms'",
        "spring.jpa.properties.jakarta.persistence.lock.timeout=250"
})
class DatabaseLockTimeoutIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldAbortContendedPessimisticLockWithinBoundedTime()
            throws Exception {
        User user = userRepository.saveAndFlush(new User(
                "lock-timeout-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var holder = executor.submit(() -> {
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            userRepository.findByIdForUpdate(user.getId())
                                    .orElseThrow();
                            acquired.countDown();
                            await(release);
                        });
            });

            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            Instant startedAt = Instant.now();

            assertThrows(
                    RuntimeException.class,
                    () -> new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status ->
                                    userRepository.findByIdForUpdate(
                                            user.getId()
                                    ).orElseThrow()
                            )
            );

            Duration elapsed = Duration.between(
                    startedAt,
                    Instant.now()
            );
            assertTrue(
                    elapsed.compareTo(Duration.ofSeconds(2)) < 0,
                    () -> "Ожидание блокировки заняло " + elapsed
            );

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            userRepository.deleteById(user.getId());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
