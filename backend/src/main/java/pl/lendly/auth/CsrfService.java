package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class CsrfService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CONTEXT_LIFETIME = Duration.ofMinutes(30);
    private final JdbcTemplate jdbc;
    private final AuthCookieSupport cookies;
    private final AuthProperties properties;

    public CsrfService(JdbcTemplate jdbc, AuthCookieSupport cookies, AuthProperties properties) {
        this.jdbc = jdbc;
        this.cookies = cookies;
        this.properties = properties;
    }

    @Transactional
    public String issueAnonymous(HttpServletResponse response) {
        UUID id = UUID.randomUUID();
        String token = randomToken();
        jdbc.update("INSERT INTO csrf_contexts (id, token_hash, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '30 minutes')",
                id, hash(token));
        cookies.setCsrfContext(response, id.toString(), CONTEXT_LIFETIME);
        return token;
    }

    @Transactional
    public String issueForSession(UUID sessionId) {
        String token = randomToken();
        int updated = jdbc.update("UPDATE auth_sessions SET csrf_token_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP",
                hash(token), sessionId);
        if (updated != 1) throw unauthorized();
        return token;
    }

    public String issueForRefresh(String refreshToken) {
        byte[] refreshHash = hash(refreshToken);
        var sessions = jdbc.query("""
                SELECT s.id FROM auth_sessions s JOIN refresh_sessions r ON r.auth_session_id = s.id
                JOIN users u ON u.id = s.user_id WHERE r.token_hash = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP AND r.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'
                """, (rs, row) -> rs.getObject("id", UUID.class), refreshHash);
        if (sessions.isEmpty()) return null;
        return issueForSession(sessions.getFirst());
    }

    public String issuePendingSessionToken() {
        return randomToken();
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void removeExpiredContexts() {
        jdbc.update("DELETE FROM csrf_contexts WHERE expires_at <= CURRENT_TIMESTAMP OR consumed_at IS NOT NULL");
    }

    @Transactional
    public void consumeAnonymous(HttpServletRequest request) {
        verifyOrigin(request);
        String idValue = cookies.read(request, cookies.csrfName());
        String supplied = request.getHeader("X-CSRF-Token");
        try {
            UUID id = UUID.fromString(idValue);
            int updated = jdbc.update("""
                    UPDATE csrf_contexts SET consumed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND token_hash = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                    """, id, hash(supplied));
            if (updated != 1) throw forbidden();
        } catch (RuntimeException exception) {
            throw forbidden();
        }
    }

    public void verifySession(HttpServletRequest request, AuthPrincipal principal) {
        verifyOrigin(request);
        String supplied = request.getHeader("X-CSRF-Token");
        if (supplied == null || supplied.length() > 256) throw forbidden();
        Boolean valid = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_sessions WHERE id = ? AND user_id = ?
                AND csrf_token_hash = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP)
                """, Boolean.class, principal.sessionId(), principal.user().id(), hash(supplied));
        if (!Boolean.TRUE.equals(valid)) throw forbidden();
    }

    public void verifyRefresh(HttpServletRequest request, byte[] refreshHash) {
        verifyOrigin(request);
        String supplied = request.getHeader("X-CSRF-Token");
        if (supplied == null || supplied.length() > 256) throw forbidden();
        Boolean active = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_sessions s JOIN refresh_sessions r ON r.auth_session_id = s.id
                JOIN users u ON u.id = s.user_id WHERE r.token_hash = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP AND r.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE')
                """, Boolean.class, refreshHash);
        if (!Boolean.TRUE.equals(active)) throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "The session is no longer valid.");
        Boolean valid = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_sessions s JOIN refresh_sessions r ON r.auth_session_id = s.id
                WHERE r.token_hash = ? AND s.csrf_token_hash = ? AND s.revoked_at IS NULL
                AND s.expires_at > CURRENT_TIMESTAMP)
                """, Boolean.class, refreshHash, hash(supplied));
        if (!Boolean.TRUE.equals(valid)) throw forbidden();
    }

    public void verifyOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank() || !properties.getAllowedOrigins().contains(origin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORIGIN_NOT_ALLOWED", "Request origin is not allowed.");
        }
    }

    static byte[] hash(String token) {
        if (token == null) return new byte[32];
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "CSRF_INVALID", "CSRF validation failed."); }
    private ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "The session is no longer valid."); }
}
