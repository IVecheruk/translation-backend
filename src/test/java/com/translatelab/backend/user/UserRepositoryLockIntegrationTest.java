package com.translatelab.backend.user;

import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class UserRepositoryLockIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldSerializePessimisticLocksForSameUser() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        UUID userId = transaction.execute(status ->
                userRepository.saveAndFlush(new User(
                        UUID.randomUUID() + "@example.com",
                        "password-hash"
                )).getId()
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        AtomicBoolean secondLockAcquired = new AtomicBoolean(false);
        Future<?> firstTransaction = null;
        Future<?> secondTransaction = null;

        try {
            firstTransaction = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        userRepository.findByIdForUpdate(userId)
                                .orElseThrow();
                        firstLockAcquired.countDown();
                        await(releaseFirstTransaction);
                    })
            );

            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            secondTransaction = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        secondTransactionStarted.countDown();
                        userRepository.findByIdForUpdate(userId)
                                .orElseThrow();
                        secondLockAcquired.set(true);
                    })
            );

            assertTrue(
                    secondTransactionStarted.await(5, TimeUnit.SECONDS)
            );
            Future<?> waitingTransaction = secondTransaction;

            assertThrows(
                    TimeoutException.class,
                    () -> waitingTransaction.get(
                            300,
                            TimeUnit.MILLISECONDS
                    )
            );
            assertFalse(secondLockAcquired.get());

            releaseFirstTransaction.countDown();
            firstTransaction.get(5, TimeUnit.SECONDS);
            secondTransaction.get(5, TimeUnit.SECONDS);

            assertTrue(secondLockAcquired.get());
        } finally {
            releaseFirstTransaction.countDown();

            if (firstTransaction != null) {
                firstTransaction.cancel(true);
            }

            if (secondTransaction != null) {
                secondTransaction.cancel(true);
            }

            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            transaction.executeWithoutResult(status ->
                    userRepository.deleteById(userId)
            );
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Ожидание синхронизации транзакций превысило лимит"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Ожидание синхронизации транзакций прервано",
                    exception
            );
        }
    }
}
