package pl.lendly.catalog;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.lendly.listings.ListingDtos.ListingSummary;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class CatalogRepository {
    private static final String DISTANCE_KM = """
            (6371.0 * 2 * asin(sqrt(
                power(sin(radians((l.public_lat::double precision - :centerLat) / 2)), 2)
                + cos(radians(:centerLat)) * cos(radians(l.public_lat::double precision))
                * power(sin(radians((l.public_lng::double precision - :centerLng) / 2)), 2))))
            """;
    private final NamedParameterJdbcTemplate named;

    public CatalogRepository(NamedParameterJdbcTemplate named) { this.named = named; }

    public List<Category> categories() {
        return named.query("""
                SELECT category_key, display_name FROM categories WHERE is_active = TRUE
                ORDER BY sort_order, category_key
                """, (rs, row) -> new Category(rs.getString("category_key"), rs.getString("display_name")));
    }

    public List<ListingSummary> search(SearchCriteria criteria, int limit, long offset) {
        MapSqlParameterSource params = parameters(criteria).addValue("limit", limit).addValue("offset", offset);
        String distance = criteria.hasCenter() ? DISTANCE_KM : "NULL::double precision";
        String order = switch (criteria.sort()) {
            case "price_asc" -> "COALESCE(l.daily_price, 0) ASC, l.published_at DESC, l.id";
            case "price_desc" -> "COALESCE(l.daily_price, 0) DESC, l.published_at DESC, l.id";
            case "distance" -> "distance_km ASC, l.published_at DESC, l.id";
            default -> "l.published_at DESC, l.id";
        };
        String sql = """
                SELECT l.id, l.title, l.category_key, l.item_condition, l.pricing_type, l.daily_price, l.currency,
                    l.public_lat, l.public_lng, l.published_at, u.display_name, u.area_label,
                    (SELECT i.id FROM listing_images i WHERE i.listing_id = l.id ORDER BY i.display_order LIMIT 1) AS cover_id,
                    %s AS distance_km
                FROM listings l JOIN users u ON u.id = l.owner_id
                %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(distance, where(criteria), order);
        return named.query(sql, params, summary());
    }

    public long count(SearchCriteria criteria) {
        Long count = named.queryForObject("SELECT count(*) FROM listings l JOIN users u ON u.id = l.owner_id " + where(criteria),
                parameters(criteria), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Listings for one Missions item: a text match is required, a category match only improves ranking. Listings busy
     * in the requested dates are excluded. Distance uses the public point, as in ordinary search.
     */
    public List<ListingSummary> missionSearch(String tsQuery, List<String> categoryKeys, java.time.LocalDate start,
                                              java.time.LocalDate end, Double centerLat, Double centerLng, Double radiusKm,
                                              BoundingBox box, int limit) {
        boolean hasCenter = centerLat != null;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tsQuery", tsQuery)
                .addValue("categories", categoryKeys.isEmpty() ? List.of("") : categoryKeys)
                .addValue("limit", limit);
        StringBuilder where = new StringBuilder("WHERE l.status = 'ACTIVE' AND u.status = 'ACTIVE'"
                + " AND l.search_vector @@ to_tsquery('simple', :tsQuery)");
        if (start != null) {
            params.addValue("start", java.sql.Date.valueOf(start)).addValue("end", java.sql.Date.valueOf(end));
            where.append(" AND NOT EXISTS (SELECT 1 FROM reservations r WHERE r.listing_id = l.id")
                    .append(" AND r.status IN ('PENDING', 'ACCEPTED', 'ACTIVE') AND r.start_date <= :end AND r.end_date >= :start)")
                    .append(" AND NOT EXISTS (SELECT 1 FROM listing_unavailable_periods p WHERE p.listing_id = l.id")
                    .append(" AND p.start_date <= :end AND p.end_date >= :start)");
        }
        if (hasCenter) {
            params.addValue("centerLat", centerLat).addValue("centerLng", centerLng).addValue("radiusKm", radiusKm)
                    .addValue("south", box.south()).addValue("north", box.north())
                    .addValue("west", box.west()).addValue("east", box.east());
            where.append(" AND l.public_lat BETWEEN :south AND :north AND l.public_lng BETWEEN :west AND :east AND ")
                    .append(DISTANCE_KM).append(" <= :radiusKm");
        }
        String sql = """
                SELECT l.id, l.title, l.category_key, l.item_condition, l.pricing_type, l.daily_price, l.currency,
                    l.public_lat, l.public_lng, l.published_at, u.display_name, u.area_label,
                    (SELECT i.id FROM listing_images i WHERE i.listing_id = l.id ORDER BY i.display_order LIMIT 1) AS cover_id,
                    %s AS distance_km
                FROM listings l JOIN users u ON u.id = l.owner_id
                %s
                ORDER BY ts_rank_cd(l.search_vector, to_tsquery('simple', :tsQuery))
                    + CASE WHEN l.category_key IN (:categories) THEN 0.5 ELSE 0 END DESC, %s l.published_at DESC, l.id
                LIMIT :limit
                """.formatted(hasCenter ? DISTANCE_KM : "NULL::double precision", where, hasCenter ? "distance_km ASC," : "");
        return named.query(sql, params, summary());
    }

    public java.util.Optional<DetailRow> activeDetail(UUID id) {
        return named.query("""
                SELECT l.*, u.display_name, u.avatar_url, u.area_label, u.created_at AS owner_registered_at
                FROM listings l JOIN users u ON u.id = l.owner_id
                WHERE l.id = :id AND l.status = 'ACTIVE' AND u.status = 'ACTIVE'
                """, new MapSqlParameterSource("id", id), (rs, row) -> new DetailRow(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getString("category_key"), rs.getString("item_condition"), rs.getString("pricing_type"),
                rs.getBigDecimal("daily_price"), rs.getString("currency"), rs.getBigDecimal("public_lat"),
                rs.getBigDecimal("public_lng"), rs.getObject("owner_id", UUID.class), rs.getString("display_name"),
                rs.getString("avatar_url"), rs.getString("area_label"), rs.getTimestamp("owner_registered_at"),
                rs.getTimestamp("published_at"), rs.getTimestamp("updated_at"))).stream().findFirst();
    }

    private String where(SearchCriteria criteria) {
        StringBuilder where = new StringBuilder("WHERE l.status = 'ACTIVE' AND u.status = 'ACTIVE'");
        if (criteria.tsQuery() != null) where.append(" AND l.search_vector @@ to_tsquery('simple', :tsQuery)");
        if (criteria.category() != null) where.append(" AND l.category_key = :category");
        if (criteria.pricing() != null) where.append(" AND l.pricing_type = :pricing");
        if (criteria.minPrice() != null) where.append(" AND COALESCE(l.daily_price, 0) >= :minPrice");
        if (criteria.maxPrice() != null) where.append(" AND COALESCE(l.daily_price, 0) <= :maxPrice");
        if (criteria.box() != null) {
            where.append(" AND l.public_lat BETWEEN :south AND :north AND l.public_lng BETWEEN :west AND :east");
        }
        if (criteria.hasCenter()) where.append(" AND ").append(DISTANCE_KM).append(" <= :radiusKm");
        return where.toString();
    }

    private MapSqlParameterSource parameters(SearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tsQuery", criteria.tsQuery())
                .addValue("category", criteria.category())
                .addValue("pricing", criteria.pricing())
                .addValue("minPrice", criteria.minPrice())
                .addValue("maxPrice", criteria.maxPrice());
        if (criteria.box() != null) {
            params.addValue("south", criteria.box().south()).addValue("north", criteria.box().north())
                    .addValue("west", criteria.box().west()).addValue("east", criteria.box().east());
        }
        if (criteria.hasCenter()) {
            params.addValue("centerLat", criteria.centerLat()).addValue("centerLng", criteria.centerLng())
                    .addValue("radiusKm", criteria.radiusKm());
        }
        return params;
    }

    private RowMapper<ListingSummary> summary() {
        return (rs, row) -> {
            UUID cover = rs.getObject("cover_id", UUID.class);
            double distance = rs.getDouble("distance_km");
            Double distanceKm = rs.wasNull() ? null : Math.round(distance * 10) / 10.0;
            Timestamp published = rs.getTimestamp("published_at");
            return new ListingSummary(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("category_key"),
                    rs.getString("item_condition"), rs.getString("pricing_type"), rs.getBigDecimal("daily_price"),
                    rs.getString("currency"), cover == null ? null : "/api/images/" + cover,
                    rs.getBigDecimal("public_lat"), rs.getBigDecimal("public_lng"), rs.getString("display_name"),
                    rs.getString("area_label"), distanceKm, published == null ? null : published.toInstant());
        };
    }

    public record Category(String key, String displayName) {}

    public record BoundingBox(BigDecimal west, BigDecimal south, BigDecimal east, BigDecimal north) {}

    public record SearchCriteria(String tsQuery, String category, String pricing, BigDecimal minPrice, BigDecimal maxPrice,
                                 BoundingBox box, Double centerLat, Double centerLng, Double radiusKm, String sort) {
        boolean hasCenter() { return centerLat != null; }
    }

    public record DetailRow(UUID id, String title, String description, String categoryKey, String condition,
                            String pricingType, BigDecimal dailyPrice, String currency, BigDecimal lat, BigDecimal lng,
                            UUID ownerId, String ownerDisplayName, String ownerAvatarUrl, String ownerAreaLabel,
                            Timestamp ownerRegisteredAt, Timestamp publishedAt, Timestamp updatedAt) {}
}
