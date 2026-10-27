package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.MfaEncryptionKey;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AES-256-GCM for TOTP secrets at rest. The stored blob is 0xA1, a key id (first byte of SHA-256 of the key), IV (12 bytes),
 * then ciphertext and tag. Blobs from before key versioning are IV, ciphertext and tag with no header and still decrypt.
 * Recovery-code hashes are HMAC-SHA-256 under a key derived from the same MFA key.
 */
@Component
public class MfaCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte VERSION = (byte) 0xA1;
    private static final int HEADER = 2;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 16;
    private final byte[] key;
    private final byte[] previousKey;

    public MfaCrypto(AuthProperties properties) {
        this.key = decode(properties.getMfaEncryptionKey());
        this.previousKey = key == null ? null : decode(properties.getMfaEncryptionKeyPrevious());
    }

    // AuthConfigurationGuard rejects a malformed key at startup, so a bad value here only means "unavailable".
    private static byte[] decode(String value) {
        if (value.isBlank()) return null;
        try { return MfaEncryptionKey.decode(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public boolean available() { return key != null; }

    /** A decrypted secret and whether it should be stored again under the current key and format. */
    record Decrypted(byte[] plain, boolean rewrap) {}

    byte[] encrypt(UUID userId, byte[] plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(userId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain);
            byte[] blob = new byte[HEADER + IV_LENGTH + sealed.length];
            blob[0] = VERSION;
            blob[1] = keyId(key);
            System.arraycopy(iv, 0, blob, HEADER, IV_LENGTH);
            System.arraycopy(sealed, 0, blob, HEADER + IV_LENGTH, sealed.length);
            return blob;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    Decrypted decrypt(UUID userId, byte[] blob) {
        requireKey();
        byte[][] candidates = previousKey == null ? new byte[][]{key} : new byte[][]{key, previousKey};
        if (blob.length >= HEADER + IV_LENGTH + TAG_LENGTH && blob[0] == VERSION) {
            for (byte[] candidate : candidates) {
                if (keyId(candidate) != blob[1]) continue;
                byte[] plain = open(candidate, userId, blob, HEADER);
                if (plain != null) return new Decrypted(plain, candidate != key);
            }
        }
        // Legacy blob, or a versioned-looking one whose first byte was a random IV byte.
        for (byte[] candidate : candidates) {
            byte[] plain = open(candidate, userId, blob, 0);
            if (plain != null) return new Decrypted(plain, true);
        }
        throw new IllegalStateException("MFA secret could not be decrypted.");
    }

    /** Keyed hash for a recovery code: current key first, then the previous key, then the pre-HMAC unkeyed hash. */
    List<byte[]> recoveryHashCandidates(UUID userId, String normalized) {
        requireKey();
        List<byte[]> hashes = new ArrayList<>();
        hashes.add(recoveryHash(userId, normalized));
        if (previousKey != null) hashes.add(recoveryHmac(previousKey, userId, normalized));
        hashes.add(legacyRecoveryHash(userId, normalized));
        return hashes;
    }

    byte[] recoveryHash(UUID userId, String normalized) {
        requireKey();
        return recoveryHmac(key, userId, normalized);
    }

    private byte[] open(byte[] candidate, UUID userId, byte[] blob, int offset) {
        if (blob.length < offset + IV_LENGTH + TAG_LENGTH) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(candidate, "AES"), new GCMParameterSpec(128, blob, offset, IV_LENGTH));
            cipher.updateAAD(userId.toString().getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(blob, offset + IV_LENGTH, blob.length - offset - IV_LENGTH);
        } catch (GeneralSecurityException exception) {
            return null;
        }
    }

    // The HMAC key is derived with a fixed label, so the AES key itself is never used as a MAC key.
    private static byte[] recoveryHmac(byte[] masterKey, UUID userId, String normalized) {
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            byte[] derived = derive.doFinal("lendly:mfa-recovery-code:v1".getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(derived, "HmacSHA256"));
            return mac.doFinal((userId + ":" + normalized).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Kept only so codes issued before the keyed hash still redeem; regenerating codes replaces them.
    private static byte[] legacyRecoveryHash(UUID userId, String normalized) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("lendly:recovery:" + userId + ":" + normalized).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte keyId(byte[] candidate) {
        try { return MessageDigest.getInstance("SHA-256").digest(candidate)[0]; }
        catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    private void requireKey() {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_UNAVAILABLE", "Multi-factor authentication is not configured.");
    }
}
