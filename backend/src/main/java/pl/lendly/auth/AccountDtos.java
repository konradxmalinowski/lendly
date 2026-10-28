package pl.lendly.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Shape of the personal data export (GET /api/me/export). */
public final class AccountDtos {
    private AccountDtos() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DataExport(Instant exportedAt, Profile profile, List<GoogleLink> google, Mfa mfa,
                             List<Listing> listings, List<Reservation> reservations, List<Report> reports,
                             List<Session> sessions) {}
    public record Profile(UUID id, String email, boolean emailVerified, String displayName, String areaLabel,
                          String avatarUrl, String role, String status, Instant createdAt) {}
    public record GoogleLink(String providerEmail, Instant linkedAt) {}
    public record Mfa(boolean enabled, Instant enabledAt, int recoveryCodesLeft) {}
    public record Listing(UUID id, String title, String description, String categoryKey, String condition,
                          String pricingType, BigDecimal dailyPrice, String currency, String status,
                          BigDecimal handoverLat, BigDecimal handoverLng, BigDecimal publicLat, BigDecimal publicLng,
                          Instant createdAt, Instant publishedAt, List<Image> images, List<Block> blocks) {}
    public record Image(UUID id, String url, int width, int height) {}
    public record Block(LocalDate startDate, LocalDate endDate) {}
    public record Reservation(UUID id, String role, UUID listingId, String listingTitle, String counterpartDisplayName,
                              String status, LocalDate startDate, LocalDate endDate, BigDecimal estimatedCost,
                              String currency, String note, String reason, Instant createdAt, Instant decidedAt,
                              Instant handedOverAt, Instant returnedAt, Instant cancelledAt) {}
    public record Report(UUID id, String targetType, UUID targetId, String reason, String details, Instant createdAt,
                         String status) {}
    public record Session(Instant createdAt, Instant expiresAt) {}
}
