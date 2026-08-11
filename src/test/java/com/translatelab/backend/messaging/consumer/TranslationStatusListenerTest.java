package com.translatelab.backend.messaging.consumer;

import com.translatelab.backend.messaging.dto.TranslationStatusMessage;
import com.translatelab.backend.messaging.exception.InvalidTranslationStatusMessageException;
import com.translatelab.backend.config.MessagingProperties;
import com.translatelab.backend.translation.entity.TranslationStatus;
import com.translatelab.backend.translation.service.TranslationStatusUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static com.translatelab.backend.support.MessagingPropertiesTestFixture.create;

@ExtendWith(MockitoExtension.class)
class TranslationStatusListenerTest {

    @Mock
    private TranslationStatusUpdateService statusUpdateService;
    @Mock
    private MessagingProperties messagingProperties;

    @InjectMocks
    private TranslationStatusListener listener;

    @Test
    void shouldDelegateMessageToUpdateService() {
        TranslationStatusMessage message = processingMessage();

        listener.consume(message);

        verify(statusUpdateService).updateStatus(message);
    }

    @Test
    void shouldRejectInvalidMessageWithoutRequeue() {
        TranslationStatusMessage message = processingMessage();
        InvalidTranslationStatusMessageException cause =
                new InvalidTranslationStatusMessageException(
                        "Некорректный progress"
                );
        doThrow(cause)
                .when(statusUpdateService)
                .updateStatus(message);

        AmqpRejectAndDontRequeueException exception = assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> listener.consume(message)
        );

        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldAllowInfrastructureFailureToReachRetryInterceptor() {
        TranslationStatusMessage message = processingMessage();
        IllegalStateException cause = new IllegalStateException(
                "Database is unavailable"
        );
        doThrow(cause)
                .when(statusUpdateService)
                .updateStatus(message);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> listener.consume(message)
        );

        assertSame(cause, exception);
    }

    @Test
    void shouldRejectOversizedRawMessageBeforeBusinessProcessing() {
        listener = new TranslationStatusListener(
                statusUpdateService,
                create()
        );
        Message rawMessage = new Message(new byte[64 * 1024 + 1]);

        assertThrows(
                AmqpRejectAndDontRequeueException.class,
                () -> listener.receive(processingMessage(), rawMessage)
        );

        verifyNoInteractions(statusUpdateService);
    }

    @Test
    void shouldSetJobCorrelationDuringRabbitProcessingAndClearItAfterward() {
        listener = new TranslationStatusListener(
                statusUpdateService,
                create()
        );
        TranslationStatusMessage message = processingMessage();
        doAnswer(invocation -> {
            assertEquals(message.jobId().toString(), MDC.get("correlationId"));
            return null;
        }).when(statusUpdateService).updateStatus(message);

        listener.receive(message, new Message(new byte[1]));

        assertNull(MDC.get("correlationId"));
    }

    private TranslationStatusMessage processingMessage() {
        return new TranslationStatusMessage(
                UUID.fromString(
                        "10cf4338-5af4-47c0-b322-c17a283a9674"
                ),
                TranslationStatus.PROCESSING,
                35,
                null,
                null
        );
    }
}
