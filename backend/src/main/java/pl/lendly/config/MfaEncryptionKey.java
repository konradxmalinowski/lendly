package pl.lendly.config;

/** Parses the AES-256 key for TOTP secrets: canonical standard Base64 of exactly 32 bytes. */
public final class MfaEncryptionKey {
    private MfaEncryptionKey() {}

    public static byte[] decode(String value) {
        byte[] decoded = JwtSecret.decode(value);
        if (decoded.length != 32) throw new IllegalArgumentException("MFA encryption key must encode exactly 32 bytes.");
        return decoded;
    }
}
