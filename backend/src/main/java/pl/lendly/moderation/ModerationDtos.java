package pl.lendly.moderation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ModerationDtos {
    private ModerationDtos() {}

    public record ReportRequest(@NotNull @Pattern(regexp = "LISTING|USER") String targetType, @NotNull UUID targetId,
                                @NotNull @Pattern(regexp = "SCAM|DANGEROUS|INAPPROPRIATE|SPAM|OTHER") String reason,
                                @Size(max = 1000) String details) {}

    public record ReportCreated(UUID id, String status) {}

    public record OutcomeRequest(@NotNull @Pattern(regexp = "RESOLVED|DISMISSED") String outcome, @Size(max = 500) String note) {}

    public record ReasonRequest(@Size(max = 500) String reason, UUID reportId) {}

    public record PersonRef(UUID id, String displayName, String role, String status) {}

    public record Target(String type, UUID id, String title, String status, String moderationReason, PersonRef user) {}

    public record ReportSummary(UUID id, String status, String reason, String details, Instant createdAt, PersonRef reporter,
                                Target target, int openReportsOnTarget) {}

    public record RelatedReport(UUID id, String status, String reason, String details, Instant createdAt, String reporterName) {}

    public record HistoryEntry(String action, String moderatorName, String reason, Instant createdAt) {}

    public record ReportDetail(UUID id, String status, String reason, String details, Instant createdAt, PersonRef reporter,
                               Target target, String resolutionNote, Instant resolvedAt, String moderatorName,
                               List<RelatedReport> relatedReports, List<HistoryEntry> history, List<String> allowedActions) {}
}
