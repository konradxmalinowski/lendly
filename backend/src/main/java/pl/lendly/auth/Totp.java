package pl.lendly.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/** RFC 6238 TOTP with the parameters every authenticator app defaults to: SHA-1, 6 digits, 30 s. */
final class Totp {
    static final int PERIOD_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {}

    static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long currentStep() { return Instant.now().getEpochSecond() / PERIOD_SECONDS; }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return "%06d".formatted(binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Returns the accepted step (within one step either side, newer than lastUsedStep) or -1. */
    static long match(byte[] secret, String candidate, long nowStep, Long lastUsedStep) {
        byte[] supplied = candidate.getBytes(StandardCharsets.UTF_8);
        long accepted = -1;
        for (long step = nowStep - 1; step <= nowStep + 1; step++) {
            // Every window is compared, so timing does not reveal which one matched.
            boolean equal = MessageDigest.isEqual(code(secret, step).getBytes(StandardCharsets.UTF_8), supplied);
            if (equal && (lastUsedStep == null || step > lastUsedStep)) accepted = step;
        }
        return accepted;
    }
}
