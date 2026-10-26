package pl.lendly.config;

import java.util.Base64;
import java.util.regex.Pattern;

/** Parses a canonical, standard-Base64 encoding of at least 256 random key bits. */
public final class JwtSecret {
    private static final Pattern CANONICAL_BASE64 = Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?");

    private JwtSecret() {}

    public static byte[] decode(String value) {
        if (value == null || value.isBlank() || !CANONICAL_BASE64.matcher(value).matches()) {
            throw new IllegalArgumentException("JWT signing key must be canonical standard Base64.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT signing key must be canonical standard Base64.");
        }
        if (decoded.length < 32 || !Base64.getEncoder().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("JWT signing key must encode at least 32 bytes.");
        }
        return decoded;
    }
}
