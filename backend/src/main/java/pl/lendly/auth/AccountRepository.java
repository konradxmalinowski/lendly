package pl.lendly.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.lendly.auth.AccountDtos.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class AccountRepository {
    static final String DELETED_NOTE = "Konto zostało usunięte.";
    static final String DELETED_LISTING = "Ogłoszenie usunięte razem z kontem.";
    static final String DELETED_NAME = "Usunięte konto";
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long otherActiveAdmins(UUID userId) {
        // Locking the rows serialises two admins deleting themselves at the same moment.
        List<UUID> admins = jdbc.query("SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class));
        return admins.stream().filter(id -> !id.equals(userId)).count();
    }

    public void lockListings(UUID ownerId) {
        jdbc.query("SELECT id FROM listings WHERE owner_id = ? ORDER BY id FOR UPDATE", (rs, row) -> 0, ownerId);
    }

    /** Cancels open reservations of the user as borrower and as owner; returns how many rows changed. */
    public int cancelOpenReservations(UUID userId) {
        int asBorrower = jdbc.update("""
                UPDATE reservations SET status = 'CANCELLED', cancelled_by = 'BORROWER', decision_reason = ?,
                    cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE borrower_id = ? AND status IN ('PENDING', 'ACCEPTED', 'ACTIVE')
                """, DELETED_NOTE, userId);
        int asOwner = jdbc.update("""
                UPDATE reservations SET status = 'CANCELLED', cancelled_by = 'OWNER', decision_reason = ?,
                    cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE status IN ('PENDING', 'ACCEPTED', 'ACTIVE')
                    AND listing_id IN (SELECT id FROM listings WHERE owner_id = ?)
                """, DELETED_NOTE, userId);
        return asBorrower + asOwner;
    }

    /** Removes free text the user wrote on reservations that stay as history for the other side. */
    public void clearReservationText(UUID userId) {
        jdbc.update("UPDATE reservations SET note = NULL WHERE borrower_id = ? AND note IS NOT NULL", userId);
        jdbc.update("""
                UPDATE reservations SET decision_reason = NULL
                WHERE decision_reason IS NOT NULL AND decision_reason <> ?
                    AND ((borrower_id = ? AND status = 'CANCELLED' AND cancelled_by = 'BORROWER')
                        OR (listing_id IN (SELECT id FROM listings WHERE owner_id = ?)
                            AND (status = 'REJECTED' OR (status = 'CANCELLED' AND cancelled_by = 'OWNER'))))
                """, DELETED_NOTE, userId, userId);
    }

    /** Archives the user's listings, strips their text and exact location, and returns the image keys to delete. */
    public List<String> retireListings(UUID ownerId) {
        List<String> keys = jdbc.queryForList("""
                SELECT i.storage_key FROM listing_images i JOIN listings l ON l.id = i.listing_id WHERE l.owner_id = ?
                """, String.class, ownerId);
        jdbc.update("DELETE FROM listing_images WHERE listing_id IN (SELECT id FROM listings WHERE owner_id = ?)", ownerId);
        jdbc.update("DELETE FROM listing_unavailable_periods WHERE listing_id IN (SELECT id FROM listings WHERE owner_id = ?)", ownerId);
        jdbc.update("""
                UPDATE listings SET status = 'ARCHIVED', description = ?, handover_lat = public_lat, handover_lng = public_lng,
                    updated_at = CURRENT_TIMESTAMP
                WHERE owner_id = ?
                """, DELETED_LISTING, ownerId);
        return keys;
    }

    public void deleteAuthData(UUID userId) {
        jdbc.update("DELETE FROM oauth_identities WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mfa_challenges WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mfa_recovery_codes WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_mfa WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM email_verification_codes WHERE user_id = ?", userId);
    }

    // The reason category stays for moderation statistics; the free text is the reporter's own words.
    public void clearReportDetails(UUID reporterId) {
        jdbc.update("UPDATE content_reports SET details = NULL WHERE reporter_id = ?", reporterId);
    }

    public void anonymise(UUID userId) {
        jdbc.update("""
                UPDATE users SET email = ?, display_name = ?, password_hash = NULL, area_label = NULL, avatar_url = NULL,
                    email_verified = FALSE, verification_failures = 0, role = 'USER', status = 'DELETED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, "deleted-" + userId + "@deleted.invalid", DELETED_NAME, userId);
    }

    public List<GoogleLink> googleLinks(UUID userId) {
        return jdbc.query("SELECT provider_email, created_at FROM oauth_identities WHERE user_id = ? ORDER BY created_at",
                (rs, row) -> new GoogleLink(rs.getString("provider_email"), instant(rs.getTimestamp("created_at"))), userId);
    }

    public Mfa mfa(UUID userId) {
        List<Instant> enabled = jdbc.query("SELECT enabled_at FROM user_mfa WHERE user_id = ? AND enabled_at IS NOT NULL",
                (rs, row) -> instant(rs.getTimestamp("enabled_at")), userId);
        if (enabled.isEmpty()) return new Mfa(false, null, 0);
        Integer left = jdbc.queryForObject("SELECT COUNT(*) FROM mfa_recovery_codes WHERE user_id = ? AND used_at IS NULL",
                Integer.class, userId);
        return new Mfa(true, enabled.getFirst(), left == null ? 0 : left);
    }

    public List<Listing> listings(UUID ownerId) {
        List<Listing> result = new ArrayList<>();
        var rows = jdbc.queryForList("SELECT * FROM listings WHERE owner_id = ? ORDER BY created_at, id", ownerId);
        for (var row : rows) {
            UUID id = (UUID) row.get("id");
            List<Image> images = jdbc.query("""
                    SELECT id, width, height FROM listing_images WHERE listing_id = ? ORDER BY display_order
                    """, (rs, n) -> new Image(rs.getObject("id", UUID.class), "/api/images/" + rs.getObject("id", UUID.class),
                    rs.getInt("width"), rs.getInt("height")), id);
            List<Block> blocks = jdbc.query("""
                    SELECT start_date, end_date FROM listing_unavailable_periods WHERE listing_id = ? ORDER BY start_date
                    """, (rs, n) -> new Block(rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate()), id);
            result.add(new Listing(id, (String) row.get("title"), (String) row.get("description"),
                    (String) row.get("category_key"), (String) row.get("item_condition"), (String) row.get("pricing_type"),
                    (java.math.BigDecimal) row.get("daily_price"), ((String) row.get("currency")).trim(),
                    (String) row.get("status"), (java.math.BigDecimal) row.get("handover_lat"),
                    (java.math.BigDecimal) row.get("handover_lng"), (java.math.BigDecimal) row.get("public_lat"),
                    (java.math.BigDecimal) row.get("public_lng"), instant((Timestamp) row.get("created_at")),
                    instant((Timestamp) row.get("published_at")), images, blocks));
        }
        return result;
    }

    public List<Reservation> reservations(UUID userId) {
        return jdbc.query("""
                SELECT r.*, l.title, l.owner_id, b.display_name AS borrower_name, o.display_name AS owner_name
                FROM reservations r JOIN listings l ON l.id = r.listing_id
                JOIN users b ON b.id = r.borrower_id JOIN users o ON o.id = l.owner_id
                WHERE r.borrower_id = ? OR l.owner_id = ? ORDER BY r.created_at, r.id
                """, (rs, n) -> {
            boolean owner = userId.equals(rs.getObject("owner_id", UUID.class));
            String status = rs.getString("status");
            String cancelledBy = rs.getString("cancelled_by");
            boolean ownReason = owner
                    ? "REJECTED".equals(status) || ("CANCELLED".equals(status) && "OWNER".equals(cancelledBy))
                    : "CANCELLED".equals(status) && "BORROWER".equals(cancelledBy);
            return new Reservation(rs.getObject("id", UUID.class), owner ? "OWNER" : "BORROWER",
                    rs.getObject("listing_id", UUID.class), rs.getString("title"),
                    owner ? rs.getString("borrower_name") : rs.getString("owner_name"), status,
                    rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
                    rs.getBigDecimal("estimated_cost"), rs.getString("currency").trim(),
                    owner ? null : rs.getString("note"), ownReason ? rs.getString("decision_reason") : null,
                    instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("decided_at")),
                    instant(rs.getTimestamp("handed_over_at")), instant(rs.getTimestamp("returned_at")),
                    instant(rs.getTimestamp("cancelled_at")));
        }, userId, userId);
    }

    public List<Report> reports(UUID userId) {
        return jdbc.query("""
                SELECT id, target_type, COALESCE(target_listing_id, target_user_id) AS target_id, reason, details,
                    created_at, status
                FROM content_reports WHERE reporter_id = ? ORDER BY created_at, id
                """, (rs, n) -> new Report(rs.getObject("id", UUID.class), rs.getString("target_type"),
                rs.getObject("target_id", UUID.class), rs.getString("reason"), rs.getString("details"),
                instant(rs.getTimestamp("created_at")), rs.getString("status")), userId);
    }

    public List<Session> sessions(UUID userId) {
        return jdbc.query("""
                SELECT created_at, expires_at FROM auth_sessions
                WHERE user_id = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP ORDER BY created_at
                """, (rs, n) -> new Session(instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("expires_at"))), userId);
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
