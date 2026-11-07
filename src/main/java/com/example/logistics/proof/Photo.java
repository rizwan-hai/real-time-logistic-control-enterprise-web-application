package com.example.logistics.proof;

import java.time.Instant;
import java.util.Objects;

public record Photo(
        String objectKey,
        String fileName,
        String mimeType,
        long sizeBytes,
        Instant capturedAt,
        String checksum,
        String signedUrl
) {
    public Photo {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Photo object key is required.");
        }
        fileName = fileName == null ? "photo" : fileName;
        mimeType = mimeType == null ? "image/jpeg" : mimeType;
        sizeBytes = Math.max(0, sizeBytes);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        checksum = checksum == null ? "" : checksum;
        signedUrl = signedUrl == null ? "" : signedUrl;
    }

    public String checksumOrEmpty() {
        return Objects.requireNonNullElse(checksum, "");
    }
}
