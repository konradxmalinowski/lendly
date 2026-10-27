package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.MfaEncryptionKey;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

/** AES-256-GCM for TOTP secrets at rest; the stored blob is IV (12 bytes) followed by ciphertext and tag. */
@Component
public class MfaCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public MfaCrypto(AuthProperties properties) {
        byte[] decoded = null;
        if (!properties.getMfaEncryptionKey().isBlank()) {
            try { decoded = MfaEncryptionKey.decode(properties.getMfaEncryptionKey()); }
            catch (IllegalArgumentException ignored) { /* AuthConfigurationGuard rejects the key at startup. */ }
        }
        this.key = decoded;
    }

    public boolean available() { return key != null; }

    byte[] encrypt(UUID userId, byte[] plain) {
        requireKey();
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(userId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain);
            byte[] blob = Arrays.copyOf(iv, iv.length + sealed.length);
            System.arraycopy(sealed, 0, blob, iv.length, sealed.length);
            return blob;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    byte[] decrypt(UUID userId, byte[] blob) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, blob, 0, 12));
            cipher.updateAAD(userId.toString().getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(blob, 12, blob.length - 12);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA secret could not be decrypted.");
        }
    }

    private void requireKey() {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_UNAVAILABLE", "Multi-factor authentication is not configured.");
    }
}
