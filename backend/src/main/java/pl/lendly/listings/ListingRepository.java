package pl.lendly.listings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ListingRepository {
    private static final RowMapper<ListingRow> LISTING = (rs, row) -> new ListingRow(
            rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("title"),
            rs.getString("description"), rs.getString("category_key"), rs.getString("item_condition"),
            rs.getString("pricing_type"), rs.getBigDecimal("daily_price"), rs.getString("currency"),
            rs.getBigDecimal("handover_lat"), rs.getBigDecimal("handover_lng"),
            rs.getBigDecimal("public_lat"), rs.getBigDecimal("public_lng"), rs.getString("status"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
            instant(rs.getTimestamp("published_at")), rs.getString("moderation_reason"));
    private static final RowMapper<ImageRow> IMAGE = (rs, row) -> new ImageRow(
            rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class), rs.getString("storage_key"),
            rs.getInt("width"), rs.getInt("height"), rs.getInt("display_order"));
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ListingRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public boolean activeCategoryExists(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM categories WHERE category_key = ? AND is_active = TRUE)", Boolean.class, key));
    }

    public ListingRow insert(UUID ownerId, ListingFields fields) {
        return jdbc.queryForObject("""
                INSERT INTO listings (owner_id, title, description, category_key, item_condition, pricing_type, daily_price,
                    handover_lat, handover_lng, public_lat, public_lng)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING *
                """, LISTING, ownerId, fields.title(), fields.description(), fields.categoryKey(), fields.condition(),
                fields.pricingType(), fields.dailyPrice(), fields.handoverLat(), fields.handoverLng(),
                fields.publicLat(), fields.publicLng());
    }

    public Optional<ListingRow> findById(UUID id) {
        return jdbc.query("SELECT * FROM listings WHERE id = ?", LISTING, id).stream().findFirst();
    }

    public Optional<ListingRow> lockById(UUID id) {
        return jdbc.query("SELECT * FROM listings WHERE id = ? FOR UPDATE", LISTING, id).stream().findFirst();
    }

    public ListingRow update(UUID id, ListingFields fields) {
        return jdbc.queryForObject("""
                UPDATE listings SET title = ?, description = ?, category_key = ?, item_condition = ?, pricing_type = ?,
                    daily_price = ?, handover_lat = ?, handover_lng = ?, public_lat = ?, public_lng = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? RETURNING *
                """, LISTING, fields.title(), fields.description(), fields.categoryKey(), fields.condition(),
                fields.pricingType(), fields.dailyPrice(), fields.handoverLat(), fields.handoverLng(),
                fields.publicLat(), fields.publicLng(), id);
    }

    public ListingRow updateStatus(UUID id, String status) {
        return jdbc.queryForObject("""
                UPDATE listings SET status = ?, updated_at = CURRENT_TIMESTAMP,
                    published_at = CASE WHEN ? = 'ACTIVE' THEN CURRENT_TIMESTAMP ELSE published_at END
                WHERE id = ? RETURNING *
                """, LISTING, status, status, id);
    }

    public List<ListingRow> findByOwner(UUID ownerId, int limit, long offset) {
        return jdbc.query("""
                SELECT * FROM listings WHERE owner_id = ?
                ORDER BY updated_at DESC, id LIMIT ? OFFSET ?
                """, LISTING, ownerId, limit, offset);
    }

    public long countByOwner(UUID ownerId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM listings WHERE owner_id = ?",
                Long.class, ownerId);
        return count == null ? 0 : count;
    }

    public List<ImageRow> images(UUID listingId) {
        return jdbc.query("SELECT * FROM listing_images WHERE listing_id = ? ORDER BY display_order", IMAGE, listingId);
    }

    public List<ImageRow> images(Collection<UUID> listingIds) {
        if (listingIds.isEmpty()) return List.of();
        return named.query("SELECT * FROM listing_images WHERE listing_id IN (:ids) ORDER BY listing_id, display_order",
                new MapSqlParameterSource("ids", listingIds), IMAGE);
    }

    public ImageRow insertImage(UUID listingId, String storageKey, int byteSize, int width, int height, int displayOrder) {
        return jdbc.queryForObject("""
                INSERT INTO listing_images (listing_id, storage_key, byte_size, width, height, display_order)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING *
                """, IMAGE, listingId, storageKey, byteSize, width, height, displayOrder);
    }

    public void deleteImage(UUID imageId) {
        jdbc.update("DELETE FROM listing_images WHERE id = ?", imageId);
    }

    public void touch(UUID listingId) {
        jdbc.update("UPDATE listings SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", listingId);
    }

    public Optional<ImageAccess> imageAccess(UUID imageId) {
        return jdbc.query("""
                SELECT i.storage_key, l.owner_id, l.status, u.status AS owner_status
                FROM listing_images i JOIN listings l ON l.id = i.listing_id JOIN users u ON u.id = l.owner_id
                WHERE i.id = ?
                """, (rs, row) -> new ImageAccess(rs.getString("storage_key"), rs.getObject("owner_id", UUID.class),
                "ACTIVE".equals(rs.getString("status")) && "ACTIVE".equals(rs.getString("owner_status"))), imageId)
                .stream().findFirst();
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record ListingFields(String title, String description, String categoryKey, String condition, String pricingType,
                                BigDecimal dailyPrice, BigDecimal handoverLat, BigDecimal handoverLng,
                                BigDecimal publicLat, BigDecimal publicLng) {}

    public record ListingRow(UUID id, UUID ownerId, String title, String description, String categoryKey, String condition,
                             String pricingType, BigDecimal dailyPrice, String currency, BigDecimal handoverLat,
                             BigDecimal handoverLng, BigDecimal publicLat, BigDecimal publicLng, String status,
                             Instant createdAt, Instant updatedAt, Instant publishedAt, String moderationReason) {}

    public record ImageRow(UUID id, UUID listingId, String storageKey, int width, int height, int displayOrder) {}

    public record ImageAccess(String storageKey, UUID ownerId, boolean publiclyVisible) {}
}
