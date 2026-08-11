package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCheckoutAttachmentCommand;
import com.translatelab.backend.payment.entity.SubscriptionPurchaseIntent;
import com.translatelab.backend.payment.exception.SubscriptionPurchaseIntentNotFoundException;
import com.translatelab.backend.payment.repository.SubscriptionPurchaseIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionPurchaseIntentCheckoutAttachmentServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000456"
    );
    private static final Instant NOW = Instant.parse(
            "2026-09-01T00:00:00Z"
    );

    @Mock
    private SubscriptionPurchaseIntentRepository intentRepository;

    @Mock
    private SubscriptionPurchaseIntent intent;

    @Mock
    private Clock clock;

    private SubscriptionPurchaseIntentCheckoutAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPurchaseIntentCheckoutAttachmentService(
                intentRepository,
                clock
        );
    }

    @Test
    void shouldLockThenReadClockAndAttachCheckout() {
        SubscriptionPurchaseIntentCheckoutAttachmentCommand command =
                validCommand();
        given(intentRepository.findByIdAndUserIdForUpdate(
                INTENT_ID,
                USER_ID
        )).willReturn(Optional.of(intent));
        given(clock.instant()).willReturn(NOW);

        service.attach(command);

        InOrder order = inOrder(intentRepository, clock, intent);
        order.verify(intentRepository).findByIdAndUserIdForUpdate(
                INTENT_ID,
                USER_ID
        );
        order.verify(clock).instant();
        order.verify(intent).attachCheckout("checkout-123", NOW);
        verifyNoMoreInteractions(intentRepository, clock, intent);
    }

    @Test
    void shouldRejectNullCommandBeforeInteractions() {
        assertThrows(
                NullPointerException.class,
                () -> service.attach(null)
        );

        verifyNoInteractions(intentRepository, clock, intent);
    }

    @Test
    void shouldHideMissingOrForeignIntentWithoutReadingClock() {
        given(intentRepository.findByIdAndUserIdForUpdate(
                INTENT_ID,
                USER_ID
        )).willReturn(Optional.empty());

        assertThrows(
                SubscriptionPurchaseIntentNotFoundException.class,
                () -> service.attach(validCommand())
        );

        verifyNoInteractions(clock, intent);
    }

    @Test
    void shouldPropagateDomainFailure() {
        IllegalStateException domainFailure = new IllegalStateException(
                "Срок действия заявки завершён"
        );
        given(intentRepository.findByIdAndUserIdForUpdate(
                INTENT_ID,
                USER_ID
        )).willReturn(Optional.of(intent));
        given(clock.instant()).willReturn(NOW);
        willThrow(domainFailure).given(intent)
                .attachCheckout("checkout-123", NOW);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> service.attach(validCommand())
        );

        assertSame(domainFailure, thrown);
    }

    @Test
    void shouldDeclareTransactionalAttachmentBoundary()
            throws NoSuchMethodException {
        Method method =
                SubscriptionPurchaseIntentCheckoutAttachmentService.class
                        .getMethod(
                                "attach",
                                SubscriptionPurchaseIntentCheckoutAttachmentCommand.class
                        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
        assertFalse(
                method.getAnnotation(Transactional.class).readOnly()
        );
    }

    private SubscriptionPurchaseIntentCheckoutAttachmentCommand validCommand() {
        return new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                USER_ID,
                INTENT_ID,
                "checkout-123"
        );
    }
}
