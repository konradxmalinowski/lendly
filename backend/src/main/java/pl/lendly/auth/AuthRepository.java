package pl.lendly.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthRepository {
    private static final RowMapper<AuthUser> USER = (rs, row) -> new AuthUser(
            rs.getObject("id", UUID.class), rs.getString("email"), rs.getBoolean("email_verified"),
            rs.getString("password_hash"), rs.getString("display_name"), rs.getString("avatar_url"),
            rs.getString("area_label"), rs.getString("role"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<AuthUser> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", USER, email).stream().findFirst();
    }
    public Optional<AuthUser> findById(UUID id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", USER, id).stream().findFirst();
    }
    public Optional<AuthUser> create(String email, String passwordHash, String displayName) {
        return jdbc.query("""
                INSERT INTO users (email, password_hash, display_name, email_verified)
                VALUES (?, ?, ?, FALSE) ON CONFLICT (email) DO NOTHING RETURNING *
                """, USER, email, passwordHash, displayName).stream().findFirst();
    }
    public Optional<AuthUser> findGoogleIdentity(String subject) {
        return jdbc.query("""
                SELECT u.* FROM oauth_identities i JOIN users u ON u.id = i.user_id
                WHERE i.provider = 'GOOGLE' AND i.provider_subject = ?
                """, USER, subject).stream().findFirst();
    }
    public AuthUser createGoogleUser(String email, String displayName, String subject) {
        AuthUser user = jdbc.query("""
                INSERT INTO users (email, email_verified, display_name)
                VALUES (?, TRUE, ?) ON CONFLICT (email) DO NOTHING RETURNING *
                """, USER, email, displayName).stream().findFirst()
                .orElseThrow(() -> new org.springframework.dao.DuplicateKeyException("Email is already registered."));
        jdbc.update("""
                INSERT INTO oauth_identities (user_id, provider, provider_subject, provider_email, provider_email_verified)
                VALUES (?, 'GOOGLE', ?, ?, TRUE)
                """, user.id(), subject, email);
        return user;
    }
    public boolean emailExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)", Boolean.class, email));
    }
    public void linkGoogle(UUID userId, String subject, String verifiedEmail) {
        jdbc.update("""
                INSERT INTO oauth_identities (user_id, provider, provider_subject, provider_email, provider_email_verified)
                VALUES (?, 'GOOGLE', ?, ?, TRUE)
                """, userId, subject, verifiedEmail);
        jdbc.update("""
                UPDATE users SET email_verified = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND email = ?
                """, userId, verifiedEmail);
    }
    public void markEmailVerified(UUID userId) {
        jdbc.update("UPDATE users SET email_verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
    }
    public AuthUser updateProfile(UUID id, boolean updateName, String displayName, boolean updateArea, String areaLabel) {
        return jdbc.queryForObject("""
                UPDATE users SET display_name = CASE WHEN ? THEN ? ELSE display_name END,
                    area_label = CASE WHEN ? THEN ? ELSE area_label END, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? RETURNING *
                """, USER, updateName, displayName, updateArea, areaLabel, id);
    }
    public boolean activeSession(UUID sessionId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.id = ? AND s.user_id = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE')
                """, Boolean.class, sessionId, userId));
    }
    public Optional<AuthUser> activeUserForSession(UUID sessionId, UUID userId) {
        return jdbc.query("""
                SELECT u.* FROM users u JOIN auth_sessions s ON s.user_id = u.id
                WHERE s.id = ? AND u.id = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP
                """, USER, sessionId, userId).stream().findFirst();
    }
}
