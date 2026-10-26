package pl.lendly.reservations;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReservationDtos {
    private ReservationDtos() {}

    public record ReservationRequest(@NotNull UUID listingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                     @Size(max = 500) String note) {}

    public record ReasonRequest(@Size(max = 300) String reason) {}

    public record BlockRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public record Block(UUID id, LocalDate startDate, LocalDate endDate) {}

    public record ListingRef(UUID id, String title, String coverImageUrl, String status) {}

    public record Counterpart(UUID id, String displayName, String areaLabel) {}

    public record ReservationSummary(UUID id, String role, String status, LocalDate startDate, LocalDate endDate, int days,
                                     String pricingType, BigDecimal estimatedCost, String currency, ListingRef listing,
                                     Counterpart counterpart, Instant expiresAt, Instant createdAt) {}

    public record ReservationDetail(UUID id, String role, String status, LocalDate startDate, LocalDate endDate, int days,
                                    String pricingType, BigDecimal dailyPrice, BigDecimal estimatedCost, String currency,
                                    String note, String decisionReason, String cancelledBy, ListingRef listing,
                                    Counterpart counterpart, BigDecimal approximateLat, BigDecimal approximateLng,
                                    BigDecimal handoverLat, BigDecimal handoverLng, List<String> allowedActions,
                                    Instant expiresAt, Instant createdAt, Instant decidedAt, Instant handedOverAt,
                                    Instant returnedAt, Instant cancelledAt) {}
}
