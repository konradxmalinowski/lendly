package pl.lendly.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.lendly.listings.ListingDtos.ListingSummary;

import java.time.LocalDate;
import java.util.List;

public final class AiDtos {
    private AiDtos() {}

    public record AiStatus(boolean available) {}

    public record Attribute(String name, String value) {}

    public record ScanResult(boolean itemRecognized, String title, String categoryKey, String description,
                             List<String> keywords, List<Attribute> attributes, List<String> mustProvide, String note) {}

    public record MissionRequest(@NotNull @Size(min = 10, max = 500) String task, LocalDate startDate, LocalDate endDate,
                                 @DecimalMin("-90") @DecimalMax("90") Double lat, @DecimalMin("-180") @DecimalMax("180") Double lng,
                                 @DecimalMin("1") @DecimalMax("50") Double radiusKm) {}

    public record MissionGroup(String name, boolean essential, String why, List<ListingSummary> listings) {}

    public record MissionResult(boolean aiUsed, String summary, String safetyNote, List<MissionGroup> groups,
                                List<ListingSummary> fallbackListings) {}
}
