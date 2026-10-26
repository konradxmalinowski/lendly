package pl.lendly.moderation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pl.lendly.moderation.ModerationDtos.HistoryEntry;
import pl.lendly.moderation.ModerationDtos.PersonRef;
import pl.lendly.moderation.ModerationDtos.RelatedReport;
import pl.lendly.moderation.ModerationDtos.Target;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ModerationRepository {
    private static final String REPORT_SELECT = """
            SELECT r.*, rep.display_name AS rep_name, rep.role AS rep_role, rep.status AS rep_status,
                l.title AS listing_title, l.status AS listing_status, l.moderation_reason,
                su.id AS subject_id, su.display_name AS subject_name, su.role AS subject_role, su.status AS subject_status,
                mdr.display_name AS moderator_name,
                (SELECT count(*) FROM content_reports o WHERE o.status = 'OPEN'
                    AND (o.target_listing_id = r.target_listing_id OR o.target_user_id = r.target_user_id)) AS open_count
            FROM content_reports r
            JOIN users rep ON rep.id = r.reporter_id
            LEFT JOIN listings l ON l.id = r.target_listing_id
            JOIN users su ON su.id = COALESCE(r.target_user_id, l.owner_id)
            LEFT JOIN users mdr ON mdr.id = r.moderator_id
            """;
    private static final RowMapper<ReportRow> REPORT = (rs, row) -> {
        String type = rs.getString("target_type");
        PersonRef subject = new PersonRef(rs.getObject("subject_id", UUID.class), rs.getString("subject_name"),
                rs.getString("subject_role"), rs.getString("subject_status"));
        Target target = "LISTING".equals(type)
                ? new Target(type, rs.getObject("target_listing_id", UUID.class), rs.getString("listing_title"),
                        rs.getString("listing_status"), rs.getString("moderation_reason"), subject)
                : new Target(type, subject.id(), subject.displayName(), subject.status(), null, subject);
        return new ReportRow(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("reason"),
                rs.getString("details"), instant(rs.getTimestamp("created_at")),
                new PersonRef(rs.getObject("reporter_id", UUID.class), rs.getString("rep_name"), rs.getString("rep_role"),
                        rs.getString("rep_status")),
                target, rs.getInt("open_count"), rs.getString("resolution_note"), instant(rs.getTimestamp("resolved_at")),
                rs.getString("moderator_name"));
    };
    private final JdbcTemplate jdbc;

    public ModerationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<PublicTarget> listingTarget(UUID listingId) {
        return jdbc.query("""
                SELECT l.owner_id, l.status, u.status AS owner_status FROM listings l JOIN users u ON u.id = l.owner_id
                WHERE l.id = ?
                """, (rs, row) -> new PublicTarget(rs.getObject("owner_id", UUID.class),
                "ACTIVE".equals(rs.getString("status")) && "ACTIVE".equals(rs.getString("owner_status"))), listingId)
                .stream().findFirst();
    }

    public Optional<PublicTarget> userTarget(UUID userId) {
        return jdbc.query("SELECT id, status FROM users WHERE id = ?", (rs, row) -> new PublicTarget(
                rs.getObject("id", UUID.class), "ACTIVE".equals(rs.getString("status"))), userId).stream().findFirst();
    }

    public UUID insertReport(UUID reporterId, String targetType, UUID targetId, String reason, String details) {
        boolean listing = "LISTING".equals(targetType);
        return jdbc.queryForObject("""
                INSERT INTO content_reports (reporter_id, target_type, target_listing_id, target_user_id, reason, details)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, reporterId, targetType, listing ? targetId : null, listing ? null : targetId, reason, details);
    }

    public List<ReportRow> queue(boolean open, String targetType, int limit, long offset) {
        String order = open ? "r.created_at ASC, r.id" : "r.resolved_at DESC, r.id";
        return jdbc.query(REPORT_SELECT + " WHERE (r.status = 'OPEN') = ? AND (?::text IS NULL OR r.target_type = ?) ORDER BY "
                + order + " LIMIT ? OFFSET ?", REPORT, open, targetType, targetType, limit, offset);
    }

    public long queueCount(boolean open, String targetType) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM content_reports r WHERE (r.status = 'OPEN') = ? AND (?::text IS NULL OR r.target_type = ?)
                """, Long.class, open, targetType, targetType);
        return count == null ? 0 : count;
    }

    public Optional<ReportRow> report(UUID id) {
        return jdbc.query(REPORT_SELECT + " WHERE r.id = ?", REPORT, id).stream().findFirst();
    }

    public Optional<String> lockReportStatus(UUID id) {
        return jdbc.query("SELECT status FROM content_reports WHERE id = ? FOR UPDATE", (rs, row) -> rs.getString(1), id)
                .stream().findFirst();
    }

    public List<RelatedReport> relatedReports(UUID reportId, Target target) {
        String column = "LISTING".equals(target.type()) ? "target_listing_id" : "target_user_id";
        return jdbc.query("""
                SELECT r.id, r.status, r.reason, r.details, r.created_at, u.display_name FROM content_reports r
                JOIN users u ON u.id = r.reporter_id WHERE r.%s = ? AND r.id <> ? ORDER BY r.created_at DESC LIMIT 50
                """.formatted(column), (rs, row) -> new RelatedReport(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("reason"), rs.getString("details"), instant(rs.getTimestamp("created_at")), rs.getString("display_name")),
                target.id(), reportId);
    }

    public List<HistoryEntry> history(UUID reportId, Target target) {
        UUID listingId = "LISTING".equals(target.type()) ? target.id() : null;
        UUID userId = target.user().id();
        return jdbc.query("""
                SELECT a.action, u.display_name, a.reason, a.created_at FROM moderation_actions a
                JOIN users u ON u.id = a.moderator_id
                WHERE a.report_id = ? OR a.target_listing_id = ? OR a.target_user_id = ?
                ORDER BY a.created_at DESC LIMIT 100
                """, (rs, row) -> new HistoryEntry(rs.getString("action"), rs.getString("display_name"), rs.getString("reason"),
                instant(rs.getTimestamp("created_at"))), reportId, listingId, userId);
    }

    public void closeReport(UUID id, String status, UUID moderatorId, String note) {
        jdbc.update("""
                UPDATE content_reports SET status = ?, moderator_id = ?, resolution_note = ?, resolved_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'OPEN'
                """, status, moderatorId, note, id);
    }

    public Optional<String> lockListingStatus(UUID listingId) {
        return jdbc.query("SELECT status FROM listings WHERE id = ? FOR UPDATE", (rs, row) -> rs.getString(1), listingId)
                .stream().findFirst();
    }

    public void setListingVisibility(UUID listingId, String status, String moderationReason) {
        // published_at is kept, so a restored listing returns to its original place in "newest" ordering.
        jdbc.update("UPDATE listings SET status = ?, moderation_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status, moderationReason, listingId);
    }

    public Optional<PersonRef> lockUser(UUID userId) {
        return jdbc.query("SELECT id, display_name, role, status FROM users WHERE id = ? FOR UPDATE", (rs, row) -> new PersonRef(
                rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("role"), rs.getString("status")), userId)
                .stream().findFirst();
    }

    public void setUserStatus(UUID userId, String status) {
        jdbc.update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, userId);
    }

    public void revokeAllSessions(UUID userId) {
        jdbc.update("UPDATE auth_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                userId);
    }

    public int rejectPendingForOwner(UUID ownerId, String reason) {
        return jdbc.update("""
                UPDATE reservations SET status = 'REJECTED', decision_reason = ?, decided_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'PENDING' AND listing_id IN (SELECT id FROM listings WHERE owner_id = ?)
                """, reason, ownerId);
    }

    public void logAction(UUID moderatorId, String action, UUID listingId, UUID userId, UUID reportId, String reason) {
        jdbc.update("""
                INSERT INTO moderation_actions (moderator_id, action, target_listing_id, target_user_id, report_id, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """, moderatorId, action, listingId, userId, reportId, reason);
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record PublicTarget(UUID ownerOrUserId, boolean publiclyVisible) {}

    public record ReportRow(UUID id, String status, String reason, String details, Instant createdAt, PersonRef reporter,
                            Target target, int openReportsOnTarget, String resolutionNote, Instant resolvedAt,
                            String moderatorName) {}
}
