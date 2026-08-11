package com.translatelab.backend.payment.service;

import com.translatelab.backend.payment.dto.PaymentCheckoutCreationCommand;
import com.translatelab.backend.payment.dto.PaymentCheckoutResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCheckoutAttachmentCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationCommand;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseIntentCreationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchasePreparationResult;
import com.translatelab.backend.payment.dto.SubscriptionPurchaseStartResponse;
import com.translatelab.backend.payment.entity.BillingPeriod;
import com.translatelab.backend.payment.exception.PaymentProviderUnavailableException;
import com.translatelab.backend.payment.provider.PaymentCheckoutGateway;
import com.translatelab.backend.payment.provider.PaymentCheckoutGatewayResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionPurchaseCheckoutServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123"
    );
    private static final UUID INTENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000456"
    );
    private static final Instant EXPIRES_AT = Instant.parse(
            "2026-09-01T00:30:00Z"
    );
    private static final URI REDIRECT_URL = URI.create(
            "https://pay.example.com/checkout/123"
    );

    @Mock
    private SubscriptionPurchaseIntentCreationService creationService;

    @Mock
    private PaymentCheckoutGatewayResolver gatewayResolver;

    @Mock
    private SubscriptionPurchaseIntentCheckoutAttachmentService
            attachmentService;

    @Mock
    private PaymentCheckoutGateway gateway;

    private SubscriptionPurchaseCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionPurchaseCheckoutService(
                creationService,
                gatewayResolver,
                attachmentService
        );
    }

    @Test
    void shouldStartCheckoutInRequiredOrderAndReturnPublicResponse() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("TRIBUTE");
        PaymentCheckoutResult checkoutResult = new PaymentCheckoutResult(
                "external-checkout-123",
                REDIRECT_URL
        );
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.createCheckout(preparation.checkoutCommand()))
                .willReturn(checkoutResult);

        SubscriptionPurchaseStartResponse response = service.start(command);

        ArgumentCaptor<SubscriptionPurchaseIntentCheckoutAttachmentCommand>
                attachmentCaptor = ArgumentCaptor.forClass(
                        SubscriptionPurchaseIntentCheckoutAttachmentCommand.class
                );
        InOrder order = inOrder(
                creationService,
                gatewayResolver,
                gateway,
                attachmentService
        );
        order.verify(creationService).prepare(command);
        order.verify(gatewayResolver).resolve("TRIBUTE");
        order.verify(gateway).createCheckout(
                preparation.checkoutCommand()
        );
        order.verify(attachmentService).attach(
                attachmentCaptor.capture()
        );

        SubscriptionPurchaseIntentCheckoutAttachmentCommand attachment =
                attachmentCaptor.getValue();
        assertAll(
                () -> assertEquals(USER_ID, attachment.userId()),
                () -> assertEquals(INTENT_ID, attachment.intentId()),
                () -> assertEquals(
                        "external-checkout-123",
                        attachment.externalCheckoutId()
                ),
                () -> assertEquals(INTENT_ID, response.intentId()),
                () -> assertEquals("TRIBUTE", response.provider()),
                () -> assertEquals(REDIRECT_URL, response.redirectUrl()),
                () -> assertEquals(EXPIRES_AT, response.expiresAt())
        );
    }

    @Test
    void shouldResolveGatewayUsingProviderFromPersistedIntentResult() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("OTHER_PROVIDER");
        PaymentCheckoutResult checkoutResult = new PaymentCheckoutResult(
                "external-checkout-123",
                REDIRECT_URL
        );
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("OTHER_PROVIDER"))
                .willReturn(gateway);
        given(gateway.createCheckout(preparation.checkoutCommand()))
                .willReturn(checkoutResult);

        SubscriptionPurchaseStartResponse response = service.start(command);

        assertEquals("OTHER_PROVIDER", response.provider());
    }

    @Test
    void shouldRejectNullCommandBeforeCollaboratorCalls() {
        assertThrows(
                NullPointerException.class,
                () -> service.start(null)
        );

        verifyNoInteractions(
                creationService,
                gatewayResolver,
                gateway,
                attachmentService
        );
    }

    @Test
    void shouldStopWhenPreparationFails() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        RuntimeException failure = new RuntimeException("preparation failed");
        given(creationService.prepare(command)).willThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.start(command)
        );

        assertSame(failure, thrown);
        verifyNoInteractions(
                gatewayResolver,
                gateway,
                attachmentService
        );
    }

    @Test
    void shouldStopWhenGatewayCannotBeResolved() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("TRIBUTE");
        PaymentProviderUnavailableException failure =
                new PaymentProviderUnavailableException();
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("TRIBUTE")).willThrow(failure);

        PaymentProviderUnavailableException thrown = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> service.start(command)
        );

        assertSame(failure, thrown);
        verifyNoInteractions(gateway, attachmentService);
    }

    @Test
    void shouldStopBeforeAttachmentWhenGatewayFails() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("TRIBUTE");
        PaymentProviderUnavailableException failure =
                new PaymentProviderUnavailableException();
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.createCheckout(preparation.checkoutCommand()))
                .willThrow(failure);

        PaymentProviderUnavailableException thrown = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> service.start(command)
        );

        assertSame(failure, thrown);
        verifyNoInteractions(attachmentService);
    }

    @Test
    void shouldRejectNullGatewayResultBeforeAttachment() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("TRIBUTE");
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.createCheckout(preparation.checkoutCommand()))
                .willReturn(null);

        NullPointerException thrown = assertThrows(
                NullPointerException.class,
                () -> service.start(command)
        );

        assertEquals(
                "Платёжный gateway не должен возвращать null",
                thrown.getMessage()
        );
        verifyNoInteractions(attachmentService);
    }

    @Test
    void shouldPropagateAttachmentFailureAfterCheckoutCreation() {
        SubscriptionPurchaseIntentCreationCommand command = validCommand();
        SubscriptionPurchasePreparationResult preparation =
                validPreparation("TRIBUTE");
        PaymentCheckoutResult checkoutResult = new PaymentCheckoutResult(
                "external-checkout-123",
                REDIRECT_URL
        );
        RuntimeException failure = new RuntimeException("attachment failed");
        given(creationService.prepare(command)).willReturn(preparation);
        given(gatewayResolver.resolve("TRIBUTE")).willReturn(gateway);
        given(gateway.createCheckout(preparation.checkoutCommand()))
                .willReturn(checkoutResult);
        org.mockito.Mockito.doThrow(failure)
                .when(attachmentService)
                .attach(
                        new SubscriptionPurchaseIntentCheckoutAttachmentCommand(
                                USER_ID,
                                INTENT_ID,
                                "external-checkout-123"
                        )
                );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.start(command)
        );

        assertSame(failure, thrown);
        org.mockito.Mockito.verify(gateway)
                .cancelCheckout("external-checkout-123");
        org.mockito.Mockito.verify(attachmentService)
                .abandon(USER_ID, INTENT_ID);
    }

    @Test
    void shouldNotDeclareTransactionAroundExternalCheckoutCall()
            throws NoSuchMethodException {
        Method method = SubscriptionPurchaseCheckoutService.class
                .getMethod(
                        "start",
                        SubscriptionPurchaseIntentCreationCommand.class
                );

        assertAll(
                () -> assertFalse(
                        SubscriptionPurchaseCheckoutService.class
                                .isAnnotationPresent(Transactional.class)
                ),
                () -> assertFalse(
                        method.isAnnotationPresent(Transactional.class)
                )
        );
    }

    private SubscriptionPurchaseIntentCreationCommand validCommand() {
        return new SubscriptionPurchaseIntentCreationCommand(
                USER_ID,
                "PRO",
                "TRIBUTE"
        );
    }

    private SubscriptionPurchasePreparationResult validPreparation(
            String provider
    ) {
        SubscriptionPurchaseIntentCreationResult intentResult =
                new SubscriptionPurchaseIntentCreationResult(
                        INTENT_ID,
                        provider,
                        EXPIRES_AT
                );
        PaymentCheckoutCreationCommand checkoutCommand =
                new PaymentCheckoutCreationCommand(
                        INTENT_ID,
                        "PRO_TRIBUTE_MONTH",
                        "PRO",
                        "Профессиональный",
                        49_900L,
                        "RUB",
                        BillingPeriod.MONTH,
                        null,
                        EXPIRES_AT
                );

        return new SubscriptionPurchasePreparationResult(
                intentResult,
                checkoutCommand
        );
    }
}
