package com.example.logistics.proof;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpService {
    private static final int DIGITS = 6;
    private static final Duration TTL = Duration.ofMinutes(5);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpRecord> codes = new ConcurrentHashMap<>();

    public String generate(String recipient, String purpose) {
        String code = String.format("%0" + DIGITS + "d", random.nextInt(1_000_000));
        codes.put(recipient + ':' + purpose, new OtpRecord(code, Instant.now().plus(TTL), false));
        return code;
    }

    public boolean verify(String recipient, String purpose, String supplied) {
        OtpRecord record = codes.get(recipient + ':' + purpose);
        if (record == null || record.expiredAt().isBefore(Instant.now()) || !record.code().equals(supplied)) {
            return false;
        }
        codes.put(recipient + ':' + purpose, new OtpRecord(record.code(), record.expiredAt(), true));
        return true;
    }

    public record OtpRecord(String code, Instant expiredAt, boolean used) {}
}
