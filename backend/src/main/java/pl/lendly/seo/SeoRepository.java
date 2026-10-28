package pl.lendly.seo;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class SeoRepository {
    static final int SITEMAP_LIMIT = 50_000;
    private final NamedParameterJdbcTemplate named;

    public SeoRepository(NamedParameterJdbcTemplate named) { this.named = named; }

    public List<SitemapEntry> activeListings() {
        return named.query("""
                SELECT l.id, l.updated_at FROM listings l JOIN users u ON u.id = l.owner_id
                WHERE l.status = 'ACTIVE' AND u.status = 'ACTIVE'
                ORDER BY l.published_at DESC, l.id
                LIMIT :limit
                """, new MapSqlParameterSource("limit", SITEMAP_LIMIT), (rs, row) -> new SitemapEntry(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("updated_at").toInstant().atZone(ZoneOffset.UTC).toLocalDate()));
    }

    public record SitemapEntry(UUID id, LocalDate lastModified) {}
}
