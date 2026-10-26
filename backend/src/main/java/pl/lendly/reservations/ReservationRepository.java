package pl.lendly.reservations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pl.lendly.reservations.ReservationDtos.Block;
import pl.lendly.reservations.ReservationDtos.DateRange;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {
    static final String BLOCKING = "('PENDING', 'ACCEPTED', 'ACTIVE')";
    private static final String SELECT = """
            SELECT r.*, l.owner_id, l.title, l.status AS listing_status, l.public_lat, l.public_lng,
                l.handover_lat, l.handover_lng,
                (SELECT i.id FROM listing_images i WHERE i.listing_id = l.id ORDER BY i.display_order LIMIT 1) AS cover_id,
                b.display_name AS borrower_name, b.area_label AS borrower_area,
                o.display_name AS owner_name, o.area_label AS owner_area
            FROM reservations r
            JOIN listings l ON l.id = r.listing_id
            JOIN users b ON b.id = r.borrower_id
            JOIN users o ON o.id = l.owner_id
            """;
    private static final RowMapper<ReservationRow> ROW = (rs, row) -> new ReservationRow(
            rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class), rs.getObject("borrower_id", UUID.class),
            rs.getObject("owner_id", UUID.class), rs.getDate("start_date").toLocalDate(), rs.getDate("end_date").toLocalDate(),
            rs.getString("status"), rs.getString("pricing_type"), rs.getBigDecimal("daily_price"), rs.getString("currency"),
            rs.getBigDecimal("estimated_cost"), rs.getString("note"), rs.getString("decision_reason"),
            rs.getString("cancelled_by"), rs.getString("title"), rs.getString("listing_status"),
            rs.getObject("cover_id", UUID.class), rs.getBigDecimal("public_lat"), rs.getBigDecimal("public_lng"),
            rs.getBigDecimal("handover_lat"), rs.getBigDecimal("handover_lng"), rs.getString("borrower_name"),
            rs.getString("borrower_area"), rs.getString("owner_name"), rs.getString("owner_area"),
            instant(rs.getTimestamp("expires_at")), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("decided_at")), instant(rs.getTimestamp("handed_over_at")),
            instant(rs.getTimestamp("returned_at")), instant(rs.getTimestamp("cancelled_at")));
    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int expireDue(LocalDate today) {
        return jdbc.update("""
                UPDATE reservations SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE status = 'PENDING' AND (expires_at <= CURRENT_TIMESTAMP OR start_date < ?)
                """, Date.valueOf(today));
    }

    public boolean overlaps(UUID listingId, LocalDate start, LocalDate end, UUID excludeReservation) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM reservations WHERE listing_id = ? AND status IN %s
                        AND start_date <= ? AND end_date >= ? AND (?::uuid IS NULL OR id <> ?::uuid)
                    UNION ALL
                    SELECT 1 FROM listing_unavailable_periods WHERE listing_id = ? AND start_date <= ? AND end_date >= ?
                )
                """.formatted(BLOCKING), Boolean.class, listingId, Date.valueOf(end), Date.valueOf(start),
                excludeReservation, excludeReservation, listingId, Date.valueOf(end), Date.valueOf(start)));
    }

    public boolean overlapsReservations(UUID listingId, LocalDate start, LocalDate end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM reservations WHERE listing_id = ? AND status IN %s
                    AND start_date <= ? AND end_date >= ?)
                """.formatted(BLOCKING), Boolean.class, listingId, Date.valueOf(end), Date.valueOf(start)));
    }

    public boolean userActive(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE')",
                Boolean.class, userId));
    }

    public int pendingCount(UUID borrowerId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE borrower_id = ? AND status = 'PENDING'",
                Integer.class, borrowerId);
        return count == null ? 0 : count;
    }

    public UUID insert(UUID listingId, UUID borrowerId, LocalDate start, LocalDate end, String pricingType,
                       BigDecimal dailyPrice, BigDecimal estimatedCost, String note, Instant expiresAt) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (listing_id, borrower_id, start_date, end_date, pricing_type, daily_price,
                    estimated_cost, note, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, listingId, borrowerId, Date.valueOf(start), Date.valueOf(end), pricingType, dailyPrice,
                estimatedCost, note, Timestamp.from(expiresAt));
    }

    public Optional<ReservationRow> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE r.id = ?", ROW, id).stream().findFirst();
    }

    public List<ReservationRow> findForBorrower(UUID borrowerId, String status, int limit, long offset) {
        return jdbc.query(SELECT + " WHERE r.borrower_id = ? AND (?::text IS NULL OR r.status = ?) "
                + "ORDER BY r.created_at DESC, r.id LIMIT ? OFFSET ?", ROW, borrowerId, status, status, limit, offset);
    }

    public long countForBorrower(UUID borrowerId, String status) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE borrower_id = ? AND (?::text IS NULL OR status = ?)",
                Long.class, borrowerId, status, status);
        return count == null ? 0 : count;
    }

    public List<ReservationRow> findForOwner(UUID ownerId, String status, int limit, long offset) {
        // Requests waiting for the owner's decision come first.
        return jdbc.query(SELECT + " WHERE l.owner_id = ? AND (?::text IS NULL OR r.status = ?) "
                + "ORDER BY (r.status = 'PENDING') DESC, r.created_at DESC, r.id LIMIT ? OFFSET ?",
                ROW, ownerId, status, status, limit, offset);
    }

    public long countForOwner(UUID ownerId, String status) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM reservations r JOIN listings l ON l.id = r.listing_id
                WHERE l.owner_id = ? AND (?::text IS NULL OR r.status = ?)
                """, Long.class, ownerId, status, status);
        return count == null ? 0 : count;
    }

    public void transition(UUID id, String status, String timestampColumn, String reason, String cancelledBy) {
        if (!List.of("decided_at", "handed_over_at", "returned_at", "cancelled_at").contains(timestampColumn)) {
            throw new IllegalArgumentException("Unknown transition column.");
        }
        jdbc.update("""
                UPDATE reservations SET status = ?, %s = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                    decision_reason = COALESCE(?, decision_reason), cancelled_by = ?
                WHERE id = ?
                """.formatted(timestampColumn), status, reason, cancelledBy, id);
    }

    public int countAcceptedOrActive(UUID listingId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE listing_id = ? AND status IN ('ACCEPTED', 'ACTIVE')",
                Integer.class, listingId);
        return count == null ? 0 : count;
    }

    public int rejectPending(UUID listingId, String reason) {
        return jdbc.update("""
                UPDATE reservations SET status = 'REJECTED', decision_reason = ?, decided_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE listing_id = ? AND status = 'PENDING'
                """, reason, listingId);
    }

    public List<DateRange> busyRanges(UUID listingId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT start_date, end_date FROM reservations
                WHERE listing_id = ? AND status IN %s AND end_date >= ? AND start_date <= ?
                UNION ALL
                SELECT start_date, end_date FROM listing_unavailable_periods
                WHERE listing_id = ? AND end_date >= ? AND start_date <= ?
                ORDER BY 1, 2
                """.formatted(BLOCKING), (rs, row) -> new DateRange(rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()),
                listingId, Date.valueOf(from), Date.valueOf(to), listingId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<Block> blocks(UUID listingId, LocalDate from) {
        return jdbc.query("""
                SELECT id, start_date, end_date FROM listing_unavailable_periods
                WHERE listing_id = ? AND end_date >= ? ORDER BY start_date
                """, (rs, row) -> new Block(rs.getObject("id", UUID.class), rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate()), listingId, Date.valueOf(from));
    }

    public boolean overlapsBlock(UUID listingId, LocalDate start, LocalDate end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM listing_unavailable_periods WHERE listing_id = ?
                    AND start_date <= ? AND end_date >= ?)
                """, Boolean.class, listingId, Date.valueOf(end), Date.valueOf(start)));
    }

    public Block insertBlock(UUID listingId, LocalDate start, LocalDate end) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO listing_unavailable_periods (listing_id, start_date, end_date) VALUES (?, ?, ?) RETURNING id
                """, UUID.class, listingId, Date.valueOf(start), Date.valueOf(end));
        return new Block(id, start, end);
    }

    public boolean deleteBlock(UUID listingId, UUID blockId) {
        return jdbc.update("DELETE FROM listing_unavailable_periods WHERE id = ? AND listing_id = ?", blockId, listingId) == 1;
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record ReservationRow(UUID id, UUID listingId, UUID borrowerId, UUID ownerId, LocalDate startDate,
                                 LocalDate endDate, String status, String pricingType, BigDecimal dailyPrice,
                                 String currency, BigDecimal estimatedCost, String note, String decisionReason,
                                 String cancelledBy, String listingTitle, String listingStatus, UUID coverImageId,
                                 BigDecimal publicLat, BigDecimal publicLng, BigDecimal handoverLat,
                                 BigDecimal handoverLng, String borrowerName, String borrowerArea, String ownerName,
                                 String ownerArea, Instant expiresAt, Instant createdAt, Instant decidedAt,
                                 Instant handedOverAt, Instant returnedAt, Instant cancelledAt) {}
}
