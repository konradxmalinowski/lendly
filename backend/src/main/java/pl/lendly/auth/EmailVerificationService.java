package pl.lendly.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.JwtSecret;
import pl.lendly.mail.MailService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VALID_MINUTES = 15;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AuthProperties properties;
    private final MailService mail;
    private final RateLimiter limiter;

    public EmailVerificationService(JdbcTemplate jdbc, TransactionTemplate transactions, AuthProperties properties,
                                    MailService mail, RateLimiter limiter) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.mail = mail;
        this.limiter = limiter;
    }

    /** Sends the first code after registration. A failure leaves the account usable; the UI offers resend. */
    public void sendInitialCode(AuthUser user) {
        try {
            String code = issue(user.id(), false);
            if (code == null || !mail.sendVerificationCode(user.email(), code, VALID_MINUTES)) {
                log.warn("Initial verification code was not delivered, userId={}", user.id());
            }
        } catch (RuntimeException exception) {
            log.warn("Initial verification code could not be issued, userId={}, exceptionType={}", user.id(),
                    exception.getClass().getSimpleName());
        }
    }

    public void resend(AuthPrincipal principal) {
        AuthUser user = principal.user();
        if (user.emailVerified()) throw alreadyVerified();
        if (!mail.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "Email delivery is not available.");
        }
        long wait = cooldownRemaining(user.id());
        if (wait > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests.", wait);
        limiter.emailResend(user.id());
        String code = issue(user.id(), true);
        if (code == null) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests.", (long) COOLDOWN_SECONDS);
        if (!mail.sendVerificationCode(user.email(), code, VALID_MINUTES)) {
            // Release the cooldown so the user can retry at once after a transport failure.
            jdbc.update("UPDATE email_verification_codes SET sent_at = sent_at - INTERVAL '60 seconds' WHERE user_id = ?", user.id());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE", "The verification email could not be sent.");
        }
    }

    /** Returns null when the cooldown applies; otherwise stores the new code HMAC and returns the plain code. */
    private String issue(UUID userId, boolean enforceCooldown) {
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        int changed = jdbc.update("""
                INSERT INTO email_verification_codes (user_id, code_hmac, expires_at, attempts, sent_at, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '15 minutes', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET code_hmac = EXCLUDED.code_hmac, expires_at = EXCLUDED.expires_at,
                    attempts = 0, sent_at = EXCLUDED.sent_at, created_at = EXCLUDED.created_at
                WHERE ? OR email_verification_codes.sent_at <= CURRENT_TIMESTAMP - INTERVAL '60 seconds'
                """, userId, hmac(userId, code), !enforceCooldown);
        return changed == 1 ? code : null;
    }

    private long cooldownRemaining(UUID userId) {
        Long wait = jdbc.query("""
                SELECT GREATEST(0, CEIL(EXTRACT(EPOCH FROM (sent_at + INTERVAL '60 seconds' - CURRENT_TIMESTAMP))))::bigint
                FROM email_verification_codes WHERE user_id = ?
                """, (rs, row) -> rs.getLong(1), userId).stream().findFirst().orElse(0L);
        return wait;
    }

    public void verify(AuthPrincipal principal, String input) {
        UUID userId = principal.user().id();
        if (principal.user().emailVerified()) throw alreadyVerified();
        limiter.emailVerify(userId);
        String code = input == null ? "" : input.replaceAll("[\\s-]", "");
        Outcome outcome = transactions.execute(status -> check(userId, code));
        if (outcome == null) throw new IllegalStateException("Verification outcome missing.");
        switch (outcome.kind) {
            case OK -> { }
            case ALREADY_VERIFIED -> throw alreadyVerified();
            case EXPIRED -> throw new ApiException(HttpStatus.GONE, "VERIFICATION_CODE_EXPIRED",
                    "The code has expired. Request a new one.");
            case EXCEEDED -> throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "Too many wrong codes. Request a new one.");
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_INVALID",
                    "The code is incorrect. Attempts remaining: " + outcome.attemptsRemaining + ".",
                    Map.of("attemptsRemaining", List.of(String.valueOf(outcome.attemptsRemaining))));
        }
    }

    // Wrong guesses are counted and committed here, so this must return an outcome instead of throwing.
    private Outcome check(UUID userId, String code) {
        var rows = jdbc.query("""
                SELECT code_hmac, expires_at > CURRENT_TIMESTAMP AS live, attempts
                FROM email_verification_codes WHERE user_id = ? FOR UPDATE
                """, (rs, row) -> new Row(rs.getBytes("code_hmac"), rs.getBoolean("live"), rs.getInt("attempts")), userId);
        if (rows.isEmpty()) {
            Boolean verified = jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, userId);
            return new Outcome(Boolean.TRUE.equals(verified) ? Kind.ALREADY_VERIFIED : Kind.EXPIRED, 0);
        }
        Row row = rows.getFirst();
        if (!row.live) return new Outcome(Kind.EXPIRED, 0);
        if (row.attempts >= MAX_ATTEMPTS) return new Outcome(Kind.EXCEEDED, 0);
        boolean match = code.matches("\\d{6}") && MessageDigest.isEqual(hmac(userId, code), row.hmac);
        if (match) {
            jdbc.update("DELETE FROM email_verification_codes WHERE user_id = ?", userId);
            jdbc.update("UPDATE users SET email_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
            return new Outcome(Kind.OK, 0);
        }
        jdbc.update("UPDATE email_verification_codes SET attempts = attempts + 1 WHERE user_id = ?", userId);
        return new Outcome(Kind.INVALID, MAX_ATTEMPTS - row.attempts - 1);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void removeStaleCodes() {
        jdbc.update("DELETE FROM email_verification_codes WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
    }

    // Key is derived from the JWT secret with a fixed label, so codes and tokens never share a MAC key.
    private byte[] hmac(UUID userId, String code) {
        if (properties.getJwtSecret().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Authentication is not configured.");
        }
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(JwtSecret.decode(properties.getJwtSecret()), "HmacSHA256"));
            byte[] key = derive.doFinal("lendly:email-verification-code:v1".getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ApiException alreadyVerified() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "The email address is already verified.");
    }

    private enum Kind { OK, INVALID, EXPIRED, EXCEEDED, ALREADY_VERIFIED }
    private record Outcome(Kind kind, int attemptsRemaining) {}
    private record Row(byte[] hmac, boolean live, int attempts) {}
}
