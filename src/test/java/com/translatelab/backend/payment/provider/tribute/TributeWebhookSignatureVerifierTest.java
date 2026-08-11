package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.config.TributeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TributeWebhookSignatureVerifierTest {

    private static final byte[] RFC_4231_BODY =
            "what do ya want for nothing?"
                    .getBytes(StandardCharsets.US_ASCII);

    private static final String RFC_4231_SIGNATURE =
            "5bdcc146bf60754e6a042426089575c75"
                    + "a003f089d2739839dec58b964ec3843";

    private TributeWebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new TributeWebhookSignatureVerifier(
                properties("Jefe")
        );
    }

    @Test
    void shouldAcceptKnownHmacSha256Vector() {
        assertTrue(verifier.isValid(
                RFC_4231_BODY,
                RFC_4231_SIGNATURE
        ));
    }

    @Test
    void shouldAcceptUppercaseHexSignature() {
        assertTrue(verifier.isValid(
                RFC_4231_BODY,
                RFC_4231_SIGNATURE.toUpperCase()
        ));
    }

    @Test
    void shouldIgnoreSurroundingHeaderWhitespace() {
        assertTrue(verifier.isValid(
                RFC_4231_BODY,
                "  " + RFC_4231_SIGNATURE + "\t"
        ));
    }

    @Test
    void shouldRejectChangedBody() {
        assertFalse(verifier.isValid(
                "what do ya want for nothing? "
                        .getBytes(StandardCharsets.US_ASCII),
                RFC_4231_SIGNATURE
        ));
    }

    @Test
    void shouldRejectChangedSignature() {
        String changedSignature = RFC_4231_SIGNATURE.substring(
                0,
                RFC_4231_SIGNATURE.length() - 1
        ) + "2";

        assertFalse(verifier.isValid(
                RFC_4231_BODY,
                changedSignature
        ));
    }

    @Test
    void shouldRejectNonHexSignatureOfCorrectLength() {
        assertFalse(verifier.isValid(
                RFC_4231_BODY,
                "z".repeat(64)
        ));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "abc", "00"})
    void shouldRejectMissingOrWrongLengthSignature(
            String signature
    ) {
        assertFalse(verifier.isValid(
                RFC_4231_BODY,
                signature
        ));
    }

    @Test
    void shouldRejectMissingBody() {
        assertFalse(verifier.isValid(null, RFC_4231_SIGNATURE));
    }

    @Test
    void shouldRejectEmptyBody() {
        assertFalse(verifier.isValid(
                new byte[0],
                RFC_4231_SIGNATURE
        ));
    }

    @Test
    void shouldRejectMissingProperties() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new TributeWebhookSignatureVerifier(null)
        );

        assertEquals(
                "Настройки Tribute не должны быть null",
                exception.getMessage()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void shouldRejectMissingApiKey(String apiKey) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TributeWebhookSignatureVerifier(
                        properties(apiKey)
                )
        );

        assertEquals(
                "API-ключ Tribute не должен быть пустым",
                exception.getMessage()
        );
    }

    private TributeProperties properties(String apiKey) {
        return new TributeProperties(
                false,
                URI.create("https://tribute.tg/api/v1"),
                apiKey,
                Duration.ofSeconds(3),
                Duration.ofSeconds(10)
        );
    }
}
