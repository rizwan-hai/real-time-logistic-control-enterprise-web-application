package com.example.logistics.proof;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ProofOfDelivery(
        UUID id,
        UUID shipmentId,
        String recipientName,
        String notes,
        List<Photo> photos,
        Signature signature,
        Double latitude,
        Double longitude,
        Instant capturedAt,
        String otpCode,
        String qrCode,
        String integrityHash,
        String podPdfKey,
        String podPdfSignedUrl,
        String status
) {
    public ProofOfDelivery {
        if (id == null) id = UUID.randomUUID();
        if (capturedAt == null) capturedAt = Instant.now();
        photos = photos == null ? List.of() : List.copyOf(new ArrayList<>(photos));
        status = status == null ? "CAPTURED" : status;
        if (integrityHash == null || integrityHash.isBlank()) {
            integrityHash = computeIntegrityHash(recipientName, notes, latitude, longitude, otpCode, qrCode, signature);
        }
    }

    public boolean hasRequiredProof() {
        return signature != null && signature.imageBytes().length > 0 && (otpCode != null || qrCode != null);
    }

    public static String computeIntegrityHash(String recipientName, String notes, Double latitude, Double longitude,
                                            String otpCode, String qrCode, Signature signature) {
        StringBuilder builder = new StringBuilder();
        if (recipientName != null) builder.append(recipientName.trim());
        if (notes != null) builder.append('|').append(notes.trim());
        if (latitude != null) builder.append('|').append(latitude);
        if (longitude != null) builder.append('|').append(longitude);
        if (otpCode != null) builder.append('|').append(otpCode);
        if (qrCode != null) builder.append('|').append(qrCode);
        if (signature != null) builder.append('|').append(signature.integrityHash());
        return Signature.computeIntegrityHash(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
