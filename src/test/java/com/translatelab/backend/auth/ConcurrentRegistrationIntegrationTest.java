package com.translatelab.backend.auth;

import com.translatelab.backend.auth.dto.RegisterRequest;
import com.translatelab.backend.auth.exception.EmailAlreadyExistsException;
import com.translatelab.backend.auth.service.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ConcurrentRegistrationIntegrationTest {

    private static final String EMAIL = "concurrent-stage5@example.com";

    @Autowired RegistrationService service;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", EMAIL);
    }

    @Test
    void shouldPersistOneAccountAndReturnOneSafeConflict() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> registerAfter(start)),
                    executor.submit(() -> registerAfter(start))
            );
            start.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Регистрация не завершилась вовремя");
            }

            int successful = 0;
            int conflicts = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get();
                    successful++;
                } catch (ExecutionException exception) {
                    if (exception.getCause()
                            instanceof EmailAlreadyExistsException) {
                        conflicts++;
                    } else {
                        throw exception;
                    }
                }
            }
            int successCount = successful;
            int conflictCount = conflicts;
            Integer stored = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE email = ?",
                    Integer.class,
                    EMAIL
            );
            assertAll(
                    () -> assertEquals(1, successCount),
                    () -> assertEquals(1, conflictCount),
                    () -> assertEquals(1, stored)
            );
        }
    }

    private Object registerAfter(CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return service.register(new RegisterRequest(EMAIL, "password123"));
    }
}
