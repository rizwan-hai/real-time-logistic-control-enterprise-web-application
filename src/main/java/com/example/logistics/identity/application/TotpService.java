package com.example.logistics.identity.application;

import org.springframework.stereotype.Service;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP: 30-second step, HMAC-SHA1, six digits, +/- one time-step tolerance. */
@Service
public class TotpService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public TotpService(Clock clock) { this.clock = clock; }

    public String newSecret() {
        byte[] bytes = new byte[20]; random.nextBytes(bytes); return base32(bytes);
    }

    public String provisioningUri(String issuer, String account, String secret) {
        String label = java.net.URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + java.net.URLEncoder.encode(issuer, StandardCharsets.UTF_8) + "&algorithm=SHA1&digits=6&period=30";
    }

    public boolean verify(String secret, String supplied) {
        if (secret == null || supplied == null || !supplied.matches("\\d{6}")) return false;
        long counter = clock.instant().getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (java.security.MessageDigest.isEqual(code(secret, counter + offset).getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) return true;
        }
        return false;
    }

    private String code(String secret, long counter) {
        try {
            byte[] key = decodeBase32(secret);
            Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception ex) { throw new IllegalArgumentException("TOTP secret is invalid", ex); }
    }

    private String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : bytes) { buffer = (buffer << 8) | (value & 0xff); bits += 8; while (bits >= 5) { out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; } }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private byte[] decodeBase32(String encoded) {
        String value = encoded.replace("=", "").toUpperCase(Locale.ROOT); byte[] out = new byte[value.length() * 5 / 8];
        int buffer = 0, bits = 0, index = 0;
        for (char ch : value.toCharArray()) { int n = ALPHABET.indexOf(ch); if (n < 0) throw new IllegalArgumentException("Invalid base32"); buffer = (buffer << 5) | n; bits += 5; if (bits >= 8) { out[index++] = (byte) (buffer >> (bits - 8)); bits -= 8; } }
        return out;
    }
}
