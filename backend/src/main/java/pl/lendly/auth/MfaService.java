package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.common.api.ApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class MfaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    // No 0, 1, i, l or o, so a code read off paper cannot be mistyped.
    private static final String RECOVERY_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MfaCrypto crypto;
    private final SessionService sessions;
    private final RateLimiter limiter;

    public MfaService(JdbcTemplate jdbc, TransactionTemplate transactions, MfaCrypto crypto, SessionService sessions,
                      RateLimiter limiter) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        this.sessions = sessions;
        this.limiter = limiter;
    }

    public static boolean required(String role) { return "MODERATOR".equals(role) || "ADMIN".equals(role); }

    public boolean available() { return crypto.available(); }

    public boolean isEnabled(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_mfa WHERE user_id = ? AND enabled_at IS NOT NULL)", Boolean.class, userId));
    }

    public Status status(AuthUser user) {
        boolean enabled = isEnabled(user.id());
        int remaining = !enabled ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL", Integer.class, user.id());
        return new Status(enabled, required(user.role()), remaining);
    }

    public Setup setup(AuthPrincipal principal) {
        requireAvailable();
        AuthUser user = principal.user();
        requireRecentSession(principal.sessionId());
        limiter.mfaManage(user.id());
        byte[] secret = Totp.newSecret();
        int stored = jdbc.update("""
                INSERT INTO user_mfa (user_id, secret_ciphertext) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET secret_ciphertext = EXCLUDED.secret_ciphertext,
                    last_used_step = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE user_mfa.enabled_at IS NULL
                """, user.id(), crypto.encrypt(user.id(), secret));
        if (stored != 1) throw alreadyEnabled();
        String base32 = Totp.base32(secret);
        Arrays.fill(secret, (byte) 0);
        String uri = "otpauth://totp/Lendly:" + encode(user.email()) + "?secret=" + base32
                + "&issuer=Lendly&algorithm=SHA1&digits=6&period=" + Totp.PERIOD_SECONDS;
        return new Setup(base32, uri, QrCodes.pngDataUri(uri));
    }

    public List<String> enable(AuthPrincipal principal, String input) {
        requireAvailable();
        UUID userId = principal.user().id();
        requireRecentSession(principal.sessionId());
        limiter.mfaManage(userId);
        String code = normalize(input);
        List<String> recovery = transactions.execute(status -> {
            MfaRow row = lock(userId);
            if (row == null) throw new ApiException(HttpStatus.CONFLICT, "MFA_SETUP_NOT_STARTED", "Start MFA setup first.");
            if (row.enabled) throw alreadyEnabled();
            requireUnlocked(row);
            if (!check(userId, row, code, false)) return null;
            jdbc.update("UPDATE user_mfa SET enabled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", userId);
            List<String> codes = replaceRecoveryCodes(userId);
            sessions.revokeOthers(userId, principal.sessionId());
            return codes;
        });
        if (recovery == null) throw invalidCode();
        return recovery;
    }

    public List<String> regenerateRecoveryCodes(AuthPrincipal principal, String input) {
        requireAvailable();
        AuthUser user = principal.user();
        requireRecentSession(principal.sessionId());
        limiter.mfaManage(user.id());
        String code = normalize(input);
        List<String> recovery = transactions.execute(status -> {
            MfaRow row = lock(user.id());
            if (row == null || !row.enabled) throw notEnabled();
            requireUnlocked(row);
            if (!check(user.id(), row, code, false)) return null;
            return replaceRecoveryCodes(user.id());
        });
        if (recovery == null) throw invalidCode();
        return recovery;
    }

    public void disable(AuthPrincipal principal, String input) {
        requireAvailable();
        AuthUser user = principal.user();
        if (required(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MFA_REQUIRED_FOR_ROLE", "This role must keep MFA enabled.");
        }
        requireRecentSession(principal.sessionId());
        limiter.mfaManage(user.id());
        String code = normalize(input);
        Boolean done = transactions.execute(status -> {
            MfaRow row = lock(user.id());
            if (row == null || !row.enabled) throw notEnabled();
            requireUnlocked(row);
            if (!check(user.id(), row, code, true)) return false;
            jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", user.id());
            jdbc.update("DELETE FROM user_mfa WHERE user_id = ?", user.id());
            sessions.revokeOthers(user.id(), principal.sessionId());
            return true;
        });
        if (!Boolean.TRUE.equals(done)) throw invalidCode();
    }

    /** Stores only the hash of the challenge token; the raw token goes into the HttpOnly cookie. */
    public String createChallenge(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                INSERT INTO mfa_challenges (user_id, token_hash, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                """, userId, CsrfService.hash(token));
        return token;
    }

    public Redemption redeemChallenge(String rawToken, String input) {
        requireAvailable();
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) return Redemption.invalid();
        byte[] tokenHash = CsrfService.hash(rawToken);
        UUID owner = jdbc.query("SELECT user_id FROM mfa_challenges WHERE token_hash = ?",
                (rs, row) -> rs.getObject("user_id", UUID.class), tokenHash).stream().findFirst().orElse(null);
        if (owner == null) return Redemption.invalid();
        limiter.mfaVerifyUser(owner);
        String code = normalize(input);
        // Wrong guesses are counted and committed in this transaction, so it returns a result instead of throwing.
        Redemption result = transactions.execute(status -> {
            var rows = jdbc.query("""
                    SELECT id, user_id, attempts, consumed_at IS NOT NULL AS consumed, expires_at > CURRENT_TIMESTAMP AS live
                    FROM mfa_challenges WHERE token_hash = ? FOR UPDATE
                    """, (rs, row) -> new ChallengeRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getInt("attempts"), rs.getBoolean("consumed"), rs.getBoolean("live")), tokenHash);
            if (rows.isEmpty()) return Redemption.invalid();
            ChallengeRow challenge = rows.getFirst();
            if (challenge.consumed || !challenge.live || challenge.attempts >= MAX_CHALLENGE_ATTEMPTS) return Redemption.invalid();
            String userStatus = jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, challenge.userId);
            MfaRow row = lock(challenge.userId);
            if (!"ACTIVE".equals(userStatus) || row == null || !row.enabled) {
                consume(challenge.id);
                return Redemption.invalid();
            }
            requireUnlocked(row);
            if (check(challenge.userId, row, code, true)) {
                consume(challenge.id);
                return Redemption.ok(challenge.userId);
            }
            jdbc.update("UPDATE mfa_challenges SET attempts = attempts + 1 WHERE id = ?", challenge.id);
            if (challenge.attempts + 1 >= MAX_CHALLENGE_ATTEMPTS) {
                consume(challenge.id);
                return Redemption.invalid();
            }
            return Redemption.wrongCode();
        });
        return result == null ? Redemption.invalid() : result;
    }

    @Scheduled(fixedDelay = 600_000)
    public void removeStaleChallenges() {
        jdbc.update("DELETE FROM mfa_challenges WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 hour'");
    }

    private void consume(UUID challengeId) {
        jdbc.update("UPDATE mfa_challenges SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", challengeId);
    }

    private MfaRow lock(UUID userId) {
        return jdbc.query("""
                SELECT secret_ciphertext, enabled_at IS NOT NULL AS enabled, last_used_step, failed_attempts,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM (COALESCE(locked_until, CURRENT_TIMESTAMP) - CURRENT_TIMESTAMP))))::bigint AS lock_seconds
                FROM user_mfa WHERE user_id = ? FOR UPDATE
                """, (rs, row) -> new MfaRow(rs.getBytes("secret_ciphertext"), rs.getBoolean("enabled"),
                (Long) rs.getObject("last_used_step"), rs.getInt("failed_attempts"), rs.getLong("lock_seconds")), userId).stream().findFirst().orElse(null);
    }

    private void requireUnlocked(MfaRow row) {
        if (row.lockSeconds > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MFA_LOCKED", "Too many wrong codes. Try again later.", row.lockSeconds);
        }
    }

    /** Persistent per-user failure count: every 5th consecutive miss locks for 15 min, doubling per block up to 24 h. */
    private boolean check(UUID userId, MfaRow row, String code, boolean allowRecovery) {
        boolean ok = allowRecovery ? matchAny(userId, row, code) : matchTotp(userId, row, code);
        if (ok) {
            jdbc.update("UPDATE user_mfa SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?", userId);
            return true;
        }
        int failed = row.failedAttempts + 1;
        long lockMinutes = failed % 5 == 0 ? Math.min(1440L, 15L << Math.min(failed / 5 - 1, 7)) : 0;
        jdbc.update("""
                UPDATE user_mfa SET failed_attempts = ?, updated_at = CURRENT_TIMESTAMP,
                    locked_until = CASE WHEN ? > 0 THEN CURRENT_TIMESTAMP + ? * INTERVAL '1 minute' ELSE locked_until END
                WHERE user_id = ?
                """, failed, lockMinutes, lockMinutes, userId);
        return false;
    }

    // Management calls need a login from the last 15 minutes, so a stolen session cannot enrol its own factor.
    private void requireRecentSession(UUID sessionId) {
        Boolean recent = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM auth_sessions WHERE id = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '15 minutes')",
                Boolean.class, sessionId);
        if (!Boolean.TRUE.equals(recent)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REAUTH_REQUIRED", "Sign in again to change multi-factor settings.");
        }
    }

    private boolean matchAny(UUID userId, MfaRow row, String code) {
        if (code.length() == 6) return matchTotp(userId, row, code);
        if (code.length() != 8 || !code.chars().allMatch(c -> RECOVERY_ALPHABET.indexOf(c) >= 0)) return false;
        for (byte[] hash : crypto.recoveryHashCandidates(userId, code)) {
            if (jdbc.update("""
                    UPDATE mfa_recovery_codes SET used_at = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND code_hash = ? AND used_at IS NULL
                    """, userId, hash) == 1) return true;
        }
        return false;
    }

    /** Accepts a code from the +/-1 step window and records the step, so the same code cannot be replayed. */
    private boolean matchTotp(UUID userId, MfaRow row, String code) {
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) return false;
        MfaCrypto.Decrypted decrypted = crypto.decrypt(userId, row.ciphertext);
        byte[] secret = decrypted.plain();
        long step;
        try {
            step = Totp.match(secret, code, Totp.currentStep(), row.lastUsedStep);
            // Moves legacy and previous-key blobs onto the current key once the user proves they work.
            if (step >= 0 && decrypted.rewrap()) {
                jdbc.update("UPDATE user_mfa SET secret_ciphertext = ? WHERE user_id = ?", crypto.encrypt(userId, secret), userId);
            }
        } finally { Arrays.fill(secret, (byte) 0); }
        if (step < 0) return false;
        jdbc.update("UPDATE user_mfa SET last_used_step = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?", step, userId);
        return true;
    }

    private List<String> replaceRecoveryCodes(UUID userId) {
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", userId);
        java.util.Set<String> plain = new java.util.LinkedHashSet<>();
        while (plain.size() < RECOVERY_CODE_COUNT) {
            StringBuilder raw = new StringBuilder();
            for (int j = 0; j < 8; j++) raw.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            plain.add(raw.toString());
        }
        List<Object[]> rows = new ArrayList<>();
        List<String> display = new ArrayList<>();
        for (String code : plain) {
            rows.add(new Object[]{userId, crypto.recoveryHash(userId, code)});
            display.add(code.substring(0, 4) + "-" + code.substring(4));
        }
        jdbc.batchUpdate("INSERT INTO mfa_recovery_codes (user_id, code_hash) VALUES (?, ?)", rows);
        return display;
    }

    /** Drops spaces and dashes and lowercases, so pasted codes and "abcd-efgh" both work. */
    static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s-]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void requireAvailable() {
        if (!crypto.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_UNAVAILABLE", "Multi-factor authentication is not configured.");
        }
    }

    private ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "MFA_CODE_INVALID", "The code is incorrect.");
    }

    private ApiException alreadyEnabled() {
        return new ApiException(HttpStatus.CONFLICT, "MFA_ALREADY_ENABLED", "MFA is already enabled.");
    }

    private ApiException notEnabled() {
        return new ApiException(HttpStatus.CONFLICT, "MFA_NOT_ENABLED", "MFA is not enabled.");
    }

    public record Status(boolean enabled, boolean required, int recoveryCodesRemaining) {}
    public record Setup(String secret, String otpauthUri, String qrImage) {}
    public record Redemption(Kind kind, UUID userId) {
        static Redemption ok(UUID userId) { return new Redemption(Kind.OK, userId); }
        static Redemption wrongCode() { return new Redemption(Kind.WRONG_CODE, null); }
        static Redemption invalid() { return new Redemption(Kind.CHALLENGE_INVALID, null); }
        public enum Kind { OK, WRONG_CODE, CHALLENGE_INVALID }
    }
    private record MfaRow(byte[] ciphertext, boolean enabled, Long lastUsedStep, int failedAttempts, long lockSeconds) {}
    private record ChallengeRow(UUID id, UUID userId, int attempts, boolean consumed, boolean live) {}
}
