package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.common.api.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class SessionService {
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AuthTokenService tokens;
    private final TransactionTemplate transactions;

    public SessionService(JdbcTemplate jdbc, AuthTokenService tokens, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.transactions = transactions;
    }

    @Transactional
    public IssuedSession create(UUID userId, String csrfToken) {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", java.sql.Timestamp.class).toInstant();
        Instant expiry = createdAt.plus(SESSION_LIFETIME);
        String refresh = randomToken();
        jdbc.update("""
                INSERT INTO auth_sessions (id, user_id, csrf_token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, sessionId, userId, CsrfService.hash(csrfToken), java.sql.Timestamp.from(expiry));
        jdbc.update("""
                INSERT INTO refresh_sessions (auth_session_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, sessionId, CsrfService.hash(refresh), java.sql.Timestamp.from(expiry));
        return new IssuedSession(sessionId, tokens.issue(userId, sessionId, Instant.now().plus(ACCESS_LIFETIME)),
                refresh, ACCESS_LIFETIME, SESSION_LIFETIME);
    }

    public Rotation rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.length() > 128) return Rotation.invalidResult();
        byte[] hash = CsrfService.hash(refreshToken);
        Rotation result = transactions.execute(status -> {
            var rows = jdbc.query("""
                    SELECT r.id refresh_id, r.auth_session_id, r.expires_at refresh_expires, r.consumed_at,
                           s.user_id, s.expires_at session_expires, s.revoked_at, u.status
                    FROM refresh_sessions r JOIN auth_sessions s ON s.id = r.auth_session_id
                    JOIN users u ON u.id = s.user_id
                    WHERE r.token_hash = ? FOR UPDATE OF r, s
                    """, (rs, row) -> new RotationRow(rs.getObject("refresh_id", UUID.class),
                    rs.getObject("auth_session_id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getTimestamp("refresh_expires").toInstant(), rs.getTimestamp("session_expires").toInstant(),
                    rs.getTimestamp("consumed_at"), rs.getTimestamp("revoked_at"), rs.getString("status")), hash);
            if (rows.isEmpty()) return Rotation.invalidResult();
            RotationRow row = rows.getFirst();
            if (row.consumedAt != null) {
                jdbc.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL", row.sessionId);
                return Rotation.invalidResult();
            }
            Instant now = Instant.now();
            if (row.revokedAt != null || !row.sessionExpiry.isAfter(now) || !row.refreshExpiry.isAfter(now)
                    || !"ACTIVE".equals(row.userStatus)) {
                jdbc.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL", row.sessionId);
                return Rotation.invalidResult();
            }
            String next = randomToken();
            UUID nextId = UUID.randomUUID();
            int consumed = jdbc.update("UPDATE refresh_sessions SET consumed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND consumed_at IS NULL",
                    row.refreshId);
            if (consumed != 1) return Rotation.invalidResult();
            Instant tokenExpiry = row.sessionExpiry;
            jdbc.update("INSERT INTO refresh_sessions (id, auth_session_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                    nextId, row.sessionId, CsrfService.hash(next), java.sql.Timestamp.from(tokenExpiry));
            jdbc.update("UPDATE refresh_sessions SET replaced_by_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    nextId, row.refreshId);
            String access = tokens.issue(row.userId, row.sessionId, now.plus(ACCESS_LIFETIME));
            return new Rotation(new IssuedSession(row.sessionId, access, next, ACCESS_LIFETIME,
                    Duration.between(now, tokenExpiry)), false);
        });
        return result == null ? Rotation.invalidResult() : result;
    }

    public void revoke(UUID sessionId) {
        jdbc.update("UPDATE auth_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
    }

    public void revokeOthers(UUID userId, UUID keepSessionId) {
        jdbc.update("""
                UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND id <> ? AND revoked_at IS NULL
                """, userId, keepSessionId);
    }

    public void revokeByRefreshHash(byte[] refreshHash) {
        jdbc.update("""
                UPDATE auth_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
                WHERE id = (SELECT auth_session_id FROM refresh_sessions WHERE token_hash = ?)
                """, refreshHash);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedSession(UUID sessionId, String accessToken, String refreshToken,
                                Duration accessLifetime, Duration refreshLifetime) {}
    public record Rotation(IssuedSession issued, boolean invalid) {
        static Rotation invalidResult() { return new Rotation(null, true); }
    }
    private record RotationRow(UUID refreshId, UUID sessionId, UUID userId, Instant refreshExpiry,
                               Instant sessionExpiry, java.sql.Timestamp consumedAt, java.sql.Timestamp revokedAt,
                               String userStatus) {}
}
