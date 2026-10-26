package pl.lendly.catalog;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.RateLimiter;
import pl.lendly.catalog.CatalogRepository.BoundingBox;
import pl.lendly.catalog.CatalogRepository.Category;
import pl.lendly.catalog.CatalogRepository.DetailRow;
import pl.lendly.catalog.CatalogRepository.SearchCriteria;
import pl.lendly.common.api.ApiException;
import pl.lendly.listings.ListingDtos.ImageResponse;
import pl.lendly.listings.ListingDtos.ListingDetail;
import pl.lendly.listings.ListingDtos.ListingSummary;
import pl.lendly.listings.ListingDtos.OwnerSummary;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.listings.ListingRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private static final Set<String> SORTS = Set.of("newest", "price_asc", "price_desc", "distance");
    private static final int MAX_TERMS = 8;
    private static final double KM_PER_DEGREE_LAT = 111.32;
    private final CatalogRepository catalog;
    private final ListingRepository listings;
    private final RateLimiter rateLimiter;

    public CatalogController(CatalogRepository catalog, ListingRepository listings, RateLimiter rateLimiter) {
        this.catalog = catalog;
        this.listings = listings;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/categories")
    ResponseEntity<List<Category>> categories() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalog.categories());
    }

    @GetMapping("/items")
    ResponseEntity<PageResponse<ListingSummary>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String pricing,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        rateLimiter.search(request);
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (q != null && q.length() > 100) errors.put("q", List.of("has an invalid length"));
        if (category != null && !category.matches("[a-z][a-z0-9_]{0,63}")) errors.put("category", List.of("is invalid"));
        if (pricing != null && !pricing.equals("FREE") && !pricing.equals("PAID")) errors.put("pricing", List.of("is invalid"));
        if (!validPrice(minPrice)) errors.put("minPrice", List.of("is invalid"));
        if (!validPrice(maxPrice)) errors.put("maxPrice", List.of("is invalid"));
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) errors.put("minPrice", List.of("is invalid"));
        BoundingBox box = bbox == null || bbox.isBlank() ? null : parseBox(bbox, errors);
        boolean anyCenter = lat != null || lng != null || radiusKm != null;
        boolean fullCenter = lat != null && lng != null && radiusKm != null;
        if (anyCenter && (!fullCenter || !finite(lat, -90, 90) || !finite(lng, -180, 180) || !finite(radiusKm, 1, 50))) {
            errors.put("lat", List.of("is invalid"));
        }
        if (!SORTS.contains(sort) || ("distance".equals(sort) && !fullCenter)) errors.put("sort", List.of("is invalid"));
        if (page < 0 || page > 10_000) errors.put("page", List.of("is invalid"));
        if (size < 1 || size > 50) errors.put("size", List.of("is invalid"));
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.", errors);
        }
        if (fullCenter && box == null) box = radiusBox(lat, lng, radiusKm);
        SearchCriteria criteria = new SearchCriteria(tsQuery(q), category, pricing, minPrice, maxPrice, box,
                fullCenter ? lat : null, fullCenter ? lng : null, fullCenter ? radiusKm : null, sort);
        long total = catalog.count(criteria);
        List<ListingSummary> items = total == 0 ? List.of() : catalog.search(criteria, size, (long) page * size);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                .body(new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size)));
    }

    @GetMapping("/items/{id}")
    ResponseEntity<ListingDetail> detail(@PathVariable String id) {
        UUID listingId;
        try { listingId = UUID.fromString(id); }
        catch (RuntimeException exception) { throw notFound(); }
        DetailRow row = catalog.activeDetail(listingId).orElseThrow(this::notFound);
        List<ImageResponse> images = listings.images(listingId).stream()
                .map(image -> new ImageResponse(image.id(), "/api/images/" + image.id(), image.width(), image.height()))
                .toList();
        OwnerSummary owner = new OwnerSummary(row.ownerId(), row.ownerDisplayName(), row.ownerAvatarUrl(),
                row.ownerAreaLabel(), row.ownerRegisteredAt().toInstant());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new ListingDetail(row.id(), row.title(),
                row.description(), row.categoryKey(), row.condition(), row.pricingType(), row.dailyPrice(), row.currency(),
                row.lat(), row.lng(), images, owner, row.publishedAt().toInstant(), row.updatedAt().toInstant()));
    }

    /** Turns free text into prefix terms joined with AND; only letters and digits survive, so no tsquery operators reach SQL. */
    public static String tsQuery(String q) {
        if (q == null) return null;
        List<String> terms = new ArrayList<>();
        for (String token : q.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty() && terms.size() < MAX_TERMS) terms.add(token + ":*");
        }
        return terms.isEmpty() ? null : String.join(" & ", terms);
    }

    private BoundingBox parseBox(String value, Map<String, List<String>> errors) {
        String[] parts = value.split(",", -1);
        try {
            if (parts.length != 4) throw new NumberFormatException();
            BigDecimal west = new BigDecimal(parts[0].trim());
            BigDecimal south = new BigDecimal(parts[1].trim());
            BigDecimal east = new BigDecimal(parts[2].trim());
            BigDecimal north = new BigDecimal(parts[3].trim());
            if (!range(west, -180, 180) || !range(east, -180, 180) || !range(south, -90, 90) || !range(north, -90, 90)
                    || west.compareTo(east) > 0 || south.compareTo(north) > 0) throw new NumberFormatException();
            return new BoundingBox(west, south, east, north);
        } catch (NumberFormatException exception) {
            errors.put("bbox", List.of("is invalid"));
            return null;
        }
    }

    /** OR of terms; each term may hold several words that must all match as prefixes. Only letters and digits survive. */
    public static String anyOfTerms(List<String> terms) {
        List<String> groups = new ArrayList<>();
        for (String term : terms) {
            String group = tsQuery(term);
            if (group != null && groups.size() < 12) groups.add("(" + group + ")");
        }
        return groups.isEmpty() ? null : String.join(" | ", groups);
    }

    public static BoundingBox radiusBox(double lat, double lng, double radiusKm) {
        double dLat = radiusKm / KM_PER_DEGREE_LAT;
        double dLng = radiusKm / (KM_PER_DEGREE_LAT * Math.max(0.01, Math.cos(Math.toRadians(lat))));
        return new BoundingBox(BigDecimal.valueOf(Math.max(-180, lng - dLng)), BigDecimal.valueOf(Math.max(-90, lat - dLat)),
                BigDecimal.valueOf(Math.min(180, lng + dLng)), BigDecimal.valueOf(Math.min(90, lat + dLat)));
    }

    private boolean validPrice(BigDecimal price) {
        return price == null || (price.signum() >= 0 && price.compareTo(new BigDecimal("9999.99")) <= 0 && price.scale() <= 2);
    }

    private boolean range(BigDecimal value, double min, double max) {
        return value.compareTo(BigDecimal.valueOf(min)) >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    private boolean finite(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "The listing was not found.");
    }
}
