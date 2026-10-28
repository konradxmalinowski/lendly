package pl.lendly.seo;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.catalog.CatalogRepository;
import pl.lendly.catalog.CatalogRepository.Category;
import pl.lendly.catalog.CatalogRepository.DetailRow;
import pl.lendly.config.AuthProperties;
import pl.lendly.listings.ListingRepository;
import pl.lendly.seo.SeoRenderer.Photo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public, read-only fragments for crawlers and link previews. The head endpoint answers 200 even when the listing is gone, because SSI ignores the status. */
@RestController
@RequestMapping("/api/seo")
public class SeoController {
    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private final CatalogRepository catalog;
    private final ListingRepository listings;
    private final SeoRepository seo;
    private final SeoRenderer renderer;
    private final AuthProperties properties;

    public SeoController(CatalogRepository catalog, ListingRepository listings, SeoRepository seo, SeoRenderer renderer,
                         AuthProperties properties) {
        this.catalog = catalog;
        this.listings = listings;
        this.seo = seo;
        this.renderer = renderer;
        this.properties = properties;
    }

    @GetMapping("/items/{id}/head")
    ResponseEntity<String> head(@PathVariable String id) {
        String body = parse(id).flatMap(catalog::activeDetail).map(this::listingHead).orElseGet(renderer::notFoundHead);
        return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(body);
    }

    @GetMapping("/sitemap-listings.xml")
    ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().contentType(XML).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(renderer.sitemap(origin(), seo.activeListings()));
    }

    private String listingHead(DetailRow row) {
        List<Photo> photos = listings.images(row.id()).stream()
                .map(image -> new Photo(image.id(), image.width(), image.height())).toList();
        String categoryName = catalog.categories().stream().filter(category -> category.key().equals(row.categoryKey()))
                .map(Category::displayName).findFirst().orElse("");
        return renderer.listingHead(origin(), row, categoryName, photos);
    }

    private Optional<UUID> parse(String id) {
        try { return Optional.of(UUID.fromString(id)); }
        catch (RuntimeException exception) { return Optional.empty(); }
    }

    private String origin() {
        String origin = properties.getPublicOrigin();
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
