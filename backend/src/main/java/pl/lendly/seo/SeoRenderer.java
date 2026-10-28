package pl.lendly.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.lendly.catalog.CatalogRepository.DetailRow;
import pl.lendly.seo.SeoRepository.SitemapEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the head fragment and sitemap. Every dynamic value passes through an escape function. */
@Component
public class SeoRenderer {
    private static final int DESCRIPTION_LIMIT = 155;
    private static final int DESCRIPTION_MIN_CUT = 80;
    private static final String SITE_NAME = "Lendly";
    private static final Map<String, String> CONDITIONS = Map.of(
            "NEW", "https://schema.org/NewCondition",
            "VERY_GOOD", "https://schema.org/UsedCondition",
            "GOOD", "https://schema.org/UsedCondition",
            "FAIR", "https://schema.org/UsedCondition");
    private final ObjectMapper mapper;

    public SeoRenderer(ObjectMapper mapper) { this.mapper = mapper; }

    public record Photo(UUID id, int width, int height) {}

    public String notFoundHead() {
        return """
                <title>Nie znaleziono ogłoszenia | Lendly</title>
                <meta name="description" content="To ogłoszenie nie jest już dostępne w Lendly.">
                <meta name="robots" content="noindex, nofollow">
                """;
    }

    public String listingHead(String origin, DetailRow row, String categoryName, List<Photo> photos) {
        String url = origin + "/items/" + row.id();
        String title = row.title() + " | " + SITE_NAME;
        String description = shorten(row.description());
        Photo cover = photos.isEmpty() ? null : photos.get(0);
        String image = cover != null ? imageUrl(origin, cover) : origin + "/icons/icon-512.png";
        int imageWidth = cover != null ? cover.width() : 512;
        int imageHeight = cover != null ? cover.height() : 512;

        List<String> lines = new ArrayList<>();
        lines.add("<title>" + escape(title) + "</title>");
        lines.add(named("description", description));
        lines.add(named("robots", "index, follow"));
        lines.add("<link rel=\"canonical\" href=\"" + escape(url) + "\">");
        lines.add(property("og:type", "website"));
        lines.add(property("og:site_name", SITE_NAME));
        lines.add(property("og:locale", "pl_PL"));
        lines.add(property("og:title", title));
        lines.add(property("og:description", description));
        lines.add(property("og:url", url));
        lines.add(property("og:image", image));
        lines.add(property("og:image:width", String.valueOf(imageWidth)));
        lines.add(property("og:image:height", String.valueOf(imageHeight)));
        if (cover != null) lines.add(property("og:image:alt", "Zdjęcie: " + row.title()));
        lines.add(named("twitter:card", cover != null ? "summary_large_image" : "summary"));
        lines.add(named("twitter:title", title));
        lines.add(named("twitter:description", description));
        lines.add(named("twitter:image", image));
        lines.add("<script type=\"application/ld+json\" id=\"lendly-jsonld\">" + jsonLd(origin, url, row, categoryName, photos)
                + "</script>");
        return String.join("\n", lines) + "\n";
    }

    public String sitemap(String origin, List<SitemapEntry> entries) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (SitemapEntry entry : entries) {
            xml.append("<url><loc>").append(escape(origin + "/items/" + entry.id())).append("</loc><lastmod>")
                    .append(entry.lastModified()).append("</lastmod></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    static String imageUrl(String origin, Photo photo) { return origin + "/api/images/" + photo.id(); }

    static String shorten(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= DESCRIPTION_LIMIT) return flat;
        String cut = flat.substring(0, DESCRIPTION_LIMIT - 1);
        int space = cut.lastIndexOf(' ');
        return cut.substring(0, space > DESCRIPTION_MIN_CUT ? space : cut.length()) + "…";
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String named(String name, String content) {
        return "<meta name=\"" + name + "\" content=\"" + escape(content) + "\">";
    }

    private static String property(String name, String content) {
        return "<meta property=\"" + name + "\" content=\"" + escape(content) + "\">";
    }

    private String jsonLd(String origin, String url, DetailRow row, String categoryName, List<Photo> photos) {
        Object price = row.dailyPrice() == null || "FREE".equals(row.pricingType()) ? (Object) 0 : row.dailyPrice();
        Map<String, Object> quantity = new LinkedHashMap<>();
        quantity.put("@type", "QuantitativeValue");
        quantity.put("value", 1);
        quantity.put("unitCode", "DAY");
        Map<String, Object> specification = new LinkedHashMap<>();
        specification.put("@type", "UnitPriceSpecification");
        specification.put("price", price);
        specification.put("priceCurrency", row.currency());
        specification.put("referenceQuantity", quantity);
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("url", url);
        offer.put("price", price);
        offer.put("priceCurrency", row.currency());
        offer.put("businessFunction", "http://purl.org/goodrelations/v1#LeaseOut");
        offer.put("priceSpecification", specification);
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("@context", "https://schema.org");
        product.put("@type", "Product");
        product.put("name", row.title());
        product.put("description", row.description());
        product.put("url", url);
        if (!photos.isEmpty()) product.put("image", photos.stream().map(photo -> imageUrl(origin, photo)).toList());
        if (categoryName != null && !categoryName.isEmpty()) product.put("category", categoryName);
        product.put("itemCondition", CONDITIONS.getOrDefault(row.condition(), "https://schema.org/UsedCondition"));
        product.put("offers", offer);
        try {
            return mapper.writeValueAsString(product).replace("<", "\\u003c");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize listing JSON-LD", exception);
        }
    }
}
