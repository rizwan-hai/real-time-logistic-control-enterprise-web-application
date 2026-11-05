package com.example.logistics.identity.application;

import com.example.logistics.identity.config.IdentityProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public SecretCipher(IdentityProperties properties) {
        try { key = MessageDigest.getInstance("SHA-256").digest(properties.jwtSecret().getBytes(StandardCharsets.UTF_8)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)); byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt identity secret", ex); }
    }
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded); byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(combined, 12, combined.length - 12), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt identity secret", ex); }
    }
}
