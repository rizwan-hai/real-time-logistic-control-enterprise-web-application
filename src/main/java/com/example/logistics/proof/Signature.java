package com.example.logistics.proof;

import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

public record Signature(
        String signerName,
        String mimeType,
        byte[] imageBytes,
        Instant capturedAt,
        String integrityHash
) {
    public Signature {
        if (signerName == null || signerName.isBlank()) {
            throw new IllegalArgumentException("Signature signer name is required.");
        }
        mimeType = mimeType == null ? "image/png" : mimeType.toLowerCase(Locale.ROOT);
        imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        integrityHash = integrityHash == null ? computeIntegrityHash(imageBytes) : integrityHash;
    }

    public String encodedImage() {
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    public boolean isValid() {
        return integrityHash.equals(computeIntegrityHash(imageBytes));
    }

    public static String computeIntegrityHash(byte[] payload) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload == null ? new byte[0] : payload);
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available on this runtime.", ex);
        }
    }
}
