package pl.lendly.moderation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.common.api.ApiException;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.moderation.ModerationDtos.OutcomeRequest;
import pl.lendly.moderation.ModerationDtos.PersonRef;
import pl.lendly.moderation.ModerationDtos.ReportCreated;
import pl.lendly.moderation.ModerationDtos.ReportDetail;
import pl.lendly.moderation.ModerationDtos.ReportRequest;
import pl.lendly.moderation.ModerationDtos.ReportSummary;
import pl.lendly.moderation.ModerationRepository.PublicTarget;
import pl.lendly.moderation.ModerationRepository.ReportRow;
import pl.lendly.reservations.ReservationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ModerationService {
    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
    private final ModerationRepository repository;
    private final ReservationRepository reservations;
    private final TransactionTemplate transactions;

    public ModerationService(ModerationRepository repository, ReservationRepository reservations, TransactionTemplate transactions) {
        this.repository = repository;
        this.reservations = reservations;
        this.transactions = transactions;
    }

    public ReportCreated report(AuthPrincipal principal, ReportRequest request) {
        UUID reporterId = principal.user().id();
        String details = trimToNull(request.details());
        if ("OTHER".equals(request.reason()) && details == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                    Map.of("details", List.of("must not be blank")));
        }
        boolean listing = "LISTING".equals(request.targetType());
        PublicTarget target = (listing ? repository.listingTarget(request.targetId()) : repository.userTarget(request.targetId()))
                .filter(PublicTarget::publiclyVisible)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TARGET_NOT_FOUND", "The reported item was not found."));
        if (target.ownerOrUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SELF_REPORT", "You cannot report yourself or your own listing.");
        }
        try {
            UUID id = repository.insertReport(reporterId, request.targetType(), request.targetId(), request.reason(), details);
            log.info("Report created, reportId={}, targetType={}", id, request.targetType());
            return new ReportCreated(id, "OPEN");
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_OPEN", "You already reported this and it is still open.");
        }
    }

    public PageResponse<ReportSummary> queue(AuthPrincipal principal, String state, String targetType, int page, int size) {
        requireModerator(principal);
        if (!"OPEN".equals(state) && !"CLOSED".equals(state)) throw invalid("state");
        if (targetType != null && !"LISTING".equals(targetType) && !"USER".equals(targetType)) throw invalid("targetType");
        if (page < 0 || page > 10_000 || size < 1 || size > 50) throw invalid("page");
        boolean open = "OPEN".equals(state);
        List<ReportSummary> items = repository.queue(open, targetType, size, (long) page * size).stream().map(this::summary).toList();
        long total = repository.queueCount(open, targetType);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public ReportDetail detail(AuthPrincipal principal, String idValue) {
        requireModerator(principal);
        ReportRow row = repository.report(parse(idValue)).orElseThrow(this::notFound);
        return new ReportDetail(row.id(), row.status(), row.reason(), row.details(), row.createdAt(), row.reporter(), row.target(),
                row.resolutionNote(), row.resolvedAt(), row.moderatorName(), repository.relatedReports(row.id(), row.target()),
                repository.history(row.id(), row.target()), allowedActions(principal, row));
    }

    public ReportDetail resolve(AuthPrincipal principal, String idValue, OutcomeRequest request) {
        requireModerator(principal);
        UUID id = parse(idValue);
        String note = trimToNull(request.note());
        transactions.executeWithoutResult(status -> {
            String current = repository.lockReportStatus(id).orElseThrow(this::notFound);
            if (!"OPEN".equals(current)) {
                throw new ApiException(HttpStatus.CONFLICT, "REPORT_CLOSED", "The report is already closed.");
            }
            repository.closeReport(id, request.outcome(), principal.user().id(), note);
            repository.logAction(principal.user().id(), "RESOLVED".equals(request.outcome()) ? "RESOLVE_REPORT" : "DISMISS_REPORT",
                    null, null, id, note);
        });
        log.info("Report closed, reportId={}, outcome={}", id, request.outcome());
        return detail(principal, idValue);
    }

    public void hideListing(AuthPrincipal principal, String listingIdValue, String reasonValue, UUID reportId) {
        requireModerator(principal);
        UUID listingId = parse(listingIdValue);
        String reason = requireReason(reasonValue);
        transactions.executeWithoutResult(status -> {
            requireReport(reportId);
            String current = repository.lockListingStatus(listingId).orElseThrow(this::notFound);
            if (!"ACTIVE".equals(current)) {
                throw new ApiException(HttpStatus.CONFLICT, "LISTING_NOT_ACTIVE", "Only a published listing can be hidden.");
            }
            repository.setListingVisibility(listingId, "HIDDEN", reason);
            reservations.rejectPending(listingId, "Ogłoszenie zostało ukryte przez moderację.");
            repository.logAction(principal.user().id(), "HIDE_LISTING", listingId, null, reportId, reason);
        });
        log.info("Listing hidden by moderation, listingId={}", listingId);
    }

    public void restoreListing(AuthPrincipal principal, String listingIdValue, String reasonValue, UUID reportId) {
        requireModerator(principal);
        UUID listingId = parse(listingIdValue);
        String reason = trimToNull(reasonValue);
        transactions.executeWithoutResult(status -> {
            requireReport(reportId);
            String current = repository.lockListingStatus(listingId).orElseThrow(this::notFound);
            if (!"HIDDEN".equals(current)) {
                throw new ApiException(HttpStatus.CONFLICT, "LISTING_NOT_HIDDEN", "The listing is not hidden.");
            }
            repository.setListingVisibility(listingId, "ACTIVE", null);
            repository.logAction(principal.user().id(), "RESTORE_LISTING", listingId, null, reportId, reason);
        });
        log.info("Listing restored by moderation, listingId={}", listingId);
    }

    public void suspendUser(AuthPrincipal principal, String userIdValue, String reasonValue, UUID reportId) {
        requireAdmin(principal);
        UUID userId = parse(userIdValue);
        String reason = requireReason(reasonValue);
        if (userId.equals(principal.user().id())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_SUSPEND_SELF", "You cannot suspend your own account.");
        }
        transactions.executeWithoutResult(status -> {
            requireReport(reportId);
            PersonRef user = repository.lockUser(userId).orElseThrow(this::notFound);
            if ("ADMIN".equals(user.role())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_SUSPEND_ADMIN", "An admin account cannot be suspended here.");
            }
            if (!"ACTIVE".equals(user.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_NOT_ACTIVE", "The account is already suspended.");
            }
            repository.setUserStatus(userId, "SUSPENDED");
            repository.revokeAllSessions(userId);
            repository.rejectPendingForOwner(userId, "Konto właściciela zostało zawieszone.");
            repository.logAction(principal.user().id(), "SUSPEND_USER", null, userId, reportId, reason);
        });
        log.info("User suspended, userId={}", userId);
    }

    public void reinstateUser(AuthPrincipal principal, String userIdValue, String reasonValue, UUID reportId) {
        requireAdmin(principal);
        UUID userId = parse(userIdValue);
        String reason = trimToNull(reasonValue);
        transactions.executeWithoutResult(status -> {
            requireReport(reportId);
            PersonRef user = repository.lockUser(userId).orElseThrow(this::notFound);
            if (!"SUSPENDED".equals(user.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_NOT_SUSPENDED", "The account is not suspended.");
            }
            repository.setUserStatus(userId, "ACTIVE");
            repository.logAction(principal.user().id(), "REINSTATE_USER", null, userId, reportId, reason);
        });
        log.info("User reinstated, userId={}", userId);
    }

    private List<String> allowedActions(AuthPrincipal principal, ReportRow row) {
        List<String> actions = new ArrayList<>();
        if ("OPEN".equals(row.status())) { actions.add("RESOLVE"); actions.add("DISMISS"); }
        if ("LISTING".equals(row.target().type())) {
            if ("ACTIVE".equals(row.target().status())) actions.add("HIDE_LISTING");
            if ("HIDDEN".equals(row.target().status())) actions.add("RESTORE_LISTING");
        }
        PersonRef subject = row.target().user();
        if (isAdmin(principal) && !subject.id().equals(principal.user().id()) && !"ADMIN".equals(subject.role())) {
            if ("ACTIVE".equals(subject.status())) actions.add("SUSPEND_USER");
            if ("SUSPENDED".equals(subject.status())) actions.add("REINSTATE_USER");
        }
        return actions;
    }

    private ReportSummary summary(ReportRow row) {
        return new ReportSummary(row.id(), row.status(), row.reason(), row.details(), row.createdAt(), row.reporter(), row.target(),
                row.openReportsOnTarget());
    }

    // The security filter chain already restricts these routes; the service re-checks so a routing mistake cannot open them.
    private void requireModerator(AuthPrincipal principal) {
        String role = principal.user().role();
        if (!"MODERATOR".equals(role) && !"ADMIN".equals(role)) throw forbidden();
    }

    private void requireAdmin(AuthPrincipal principal) {
        if (!isAdmin(principal)) throw forbidden();
    }

    private boolean isAdmin(AuthPrincipal principal) { return "ADMIN".equals(principal.user().role()); }

    private String requireReason(String value) {
        String reason = trimToNull(value);
        if (reason == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                    Map.of("reason", List.of("must not be blank")));
        }
        return reason;
    }

    private void requireReport(UUID reportId) {
        if (reportId != null && repository.lockReportStatus(reportId).isEmpty()) throw notFound();
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private UUID parse(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw notFound(); }
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "The resource was not found."); }

    private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this action."); }

    private ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                Map.of(field, List.of("is invalid")));
    }
}
