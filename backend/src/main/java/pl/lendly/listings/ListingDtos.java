package pl.lendly.listings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ListingDtos {
    private ListingDtos() {}

    public record ListingRequest(
            @NotBlank @Size(min = 3, max = 100) String title,
            @NotBlank @Size(min = 10, max = 2000) String description,
            @NotBlank @Size(max = 64) String categoryKey,
            @NotNull @Pattern(regexp = "NEW|VERY_GOOD|GOOD|FAIR") String condition,
            @NotNull @Pattern(regexp = "FREE|PAID") String pricingType,
            @DecimalMin("0.01") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal dailyPrice,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal handoverLat,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal handoverLng
    ) {}

    public record ImageResponse(UUID id, String url, int width, int height) {}

    public record OwnerSummary(UUID id, String displayName, String avatarUrl, String areaLabel, Instant registeredAt) {}

    public record OwnerListing(UUID id, String title, String description, String categoryKey, String condition,
                               String pricingType, BigDecimal dailyPrice, String currency,
                               BigDecimal handoverLat, BigDecimal handoverLng, BigDecimal publicLat, BigDecimal publicLng,
                               String status, List<ImageResponse> images, Instant createdAt, Instant updatedAt,
                               Instant publishedAt, String moderationReason) {}

    public record ListingSummary(UUID id, String title, String categoryKey, String condition, String pricingType,
                                 BigDecimal dailyPrice, String currency, String coverImageUrl,
                                 BigDecimal lat, BigDecimal lng, String ownerDisplayName, String areaLabel,
                                 Double distanceKm, Instant publishedAt) {}

    public record ListingDetail(UUID id, String title, String description, String categoryKey, String condition,
                                String pricingType, BigDecimal dailyPrice, String currency,
                                BigDecimal lat, BigDecimal lng, List<ImageResponse> images, OwnerSummary owner,
                                Instant publishedAt, Instant updatedAt) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}

    static String imageUrl(UUID imageId) { return imageId == null ? null : "/api/images/" + imageId; }
}
