package org.example.services;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class WebhookSignatureService {
    private static final String HMAC_SHA256 = "HmacSHA256";

    public boolean verifySignature(String rawPayload, String headerSignature, String secretKey) {
        if (rawPayload == null || secretKey == null || headerSignature == null) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    headerSignature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not calculate webhook signature", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
