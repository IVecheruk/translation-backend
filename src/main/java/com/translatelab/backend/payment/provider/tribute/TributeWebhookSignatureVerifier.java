package com.translatelab.backend.payment.provider.tribute;

import com.translatelab.backend.config.TributeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "app.payment.tribute",
        name = "enabled",
        havingValue = "true"
)
public class TributeWebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SHA_256_HEX_LENGTH = 64;

    private final byte[] apiKey;

    public TributeWebhookSignatureVerifier(
            TributeProperties properties
    ) {
        Objects.requireNonNull(
                properties,
                "Настройки Tribute не должны быть null"
        );

        String configuredApiKey = properties.apiKey();

        if (configuredApiKey == null
                || configuredApiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "API-ключ Tribute не должен быть пустым"
            );
        }

        this.apiKey = configuredApiKey.getBytes(
                StandardCharsets.UTF_8
        );
    }

    public boolean isValid(
            byte[] rawBody,
            String signature
    ) {
        if (rawBody == null
                || rawBody.length == 0
                || signature == null) {
            return false;
        }

        String normalizedSignature = signature.strip();

        if (normalizedSignature.length()
                != SHA_256_HEX_LENGTH) {
            return false;
        }

        byte[] receivedSignature;

        try {
            receivedSignature = HexFormat
                    .of()
                    .parseHex(normalizedSignature);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        byte[] expectedSignature =
                calculateSignature(rawBody);

        return MessageDigest.isEqual(
                expectedSignature,
                receivedSignature
        );
    }

    private byte[] calculateSignature(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(
                    new SecretKeySpec(
                            apiKey,
                            HMAC_ALGORITHM
                    )
            );

            return mac.doFinal(rawBody);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Не удалось проверить подпись webhook Tribute",
                    exception
            );
        }
    }
}
