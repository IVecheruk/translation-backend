package com.translatelab.backend.payment;

import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.entity.PlanPaymentOffer;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.repository.PlanPaymentOfferRepository;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import com.translatelab.backend.plan.entity.SubscriptionPlan;
import com.translatelab.backend.plan.repository.SubscriptionPlanRepository;
import com.translatelab.backend.user.entity.User;
import com.translatelab.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
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
class SubscriptionPurchaseIntentRepositoryLockIntegrationTest {

    @Autowired
    private SubscriptionPurchaseIntentRepository repository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private PlanPaymentOfferRepository offerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldSerializeOwnerAndProviderOperationsForSameIntent()
            throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );
        TestData testData = transaction.execute(status -> {
            User user = userRepository.saveAndFlush(
                    new User(
                            UUID.randomUUID() + "@example.com",
                            "password-hash"
                    )
            );
            SubscriptionPlan plan = planRepository.saveAndFlush(
                    new SubscriptionPlan(
                            uniquePlanCode(),
                            "Тестовый тариф"
                    )
            );
            PlanPaymentOffer offer = offerRepository.saveAndFlush(
                    new PlanPaymentOffer(
                            uniqueOfferCode(),
                            plan,
                            "TRIBUTE",
                            99900,
                            "RUB",
                            BillingPeriod.MONTH,
                            "product-" + UUID.randomUUID()
                    )
            );
            Instant now = Instant.now();
            SubscriptionPurchaseIntent intent = repository.saveAndFlush(
                    SubscriptionPurchaseIntent.pending(
                            user,
                            offer,
                            now,
                            now.plusSeconds(1800)
                    )
            );
            intent.attachCheckout("checkout-123", now);
            repository.flush();

            return new TestData(
                    intent.getId(),
                    user.getId(),
                    plan.getCode(),
                    offer.getCode(),
                    "TRIBUTE",
                    "checkout-123"
            );
        });
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
                        repository.findByIdAndUserIdForUpdate(
                                        testData.intentId(),
                                        testData.userId()
                                )
                                .orElseThrow();
                        firstLockAcquired.countDown();
                        await(releaseFirstTransaction);
                    })
            );

            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            secondTransaction = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        secondTransactionStarted.countDown();
                        repository
                                .findByProviderAndExternalCheckoutIdForUpdate(
                                        testData.provider(),
                                        testData.externalCheckoutId()
                                )
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
            transaction.executeWithoutResult(status -> {
                repository.deleteById(testData.intentId());
                repository.flush();
                userRepository.deleteById(testData.userId());
                offerRepository.deleteById(testData.offerCode());
                offerRepository.flush();
                planRepository.deleteById(testData.planCode());
            });
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

    private String uniquePlanCode() {
        return "TEST_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private String uniqueOfferCode() {
        return "OFFER_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    private record TestData(
            UUID intentId,
            UUID userId,
            String planCode,
            String offerCode,
            String provider,
            String externalCheckoutId
    ) {
    }
}
