package pl.lendly.reservations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.common.api.ApiException;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.listings.ListingRepository;
import pl.lendly.listings.ListingRepository.ListingRow;
import pl.lendly.reservations.ReservationDtos.Block;
import pl.lendly.reservations.ReservationDtos.BlockRequest;
import pl.lendly.reservations.ReservationDtos.Counterpart;
import pl.lendly.reservations.ReservationDtos.DateRange;
import pl.lendly.reservations.ReservationDtos.ListingRef;
import pl.lendly.reservations.ReservationDtos.ReservationDetail;
import pl.lendly.reservations.ReservationDtos.ReservationRequest;
import pl.lendly.reservations.ReservationDtos.ReservationSummary;
import pl.lendly.reservations.ReservationRepository.ReservationRow;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {
    static final ZoneId CALENDAR = ZoneId.of("Europe/Warsaw");
    private static final Duration PENDING_LIFETIME = Duration.ofHours(48);
    private static final int MAX_DAYS = 30;
    private static final int MAX_DAYS_AHEAD = 180;
    private static final int MAX_PENDING = 5;
    private static final int MAX_BLOCK_DAYS = 180;
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final ReservationRepository repository;
    private final ListingRepository listings;
    private final TransactionTemplate transactions;

    public ReservationService(ReservationRepository repository, ListingRepository listings, TransactionTemplate transactions) {
        this.repository = repository;
        this.listings = listings;
        this.transactions = transactions;
    }

    static LocalDate today() { return LocalDate.now(CALENDAR); }

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void expirePending() {
        int expired = repository.expireDue(today());
        if (expired > 0) log.info("Pending reservations expired, count={}", expired);
    }

    public ReservationDetail create(AuthPrincipal principal, ReservationRequest request) {
        UUID borrowerId = principal.user().id();
        LocalDate today = today();
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (start.isBefore(today)) errors.put("startDate", List.of("must not be in the past"));
        if (start.isAfter(today.plusDays(MAX_DAYS_AHEAD))) errors.put("startDate", List.of("is too far ahead"));
        if (end.isBefore(start)) errors.put("endDate", List.of("must not be before startDate"));
        else if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) errors.put("endDate", List.of("exceeds 30 days"));
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES", "The dates are not allowed.", errors);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();

        UUID id;
        try {
            id = transactions.execute(status -> {
                repository.expireDue(today);
                ListingRow listing = listings.lockById(request.listingId()).orElse(null);
                if (listing == null || !"ACTIVE".equals(listing.status()) || !repository.userActive(listing.ownerId())) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LISTING_UNAVAILABLE", "The listing cannot be reserved.");
                }
                if (listing.ownerId().equals(borrowerId)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OWN_LISTING", "You cannot reserve your own listing.");
                }
                if (repository.pendingCount(borrowerId) >= MAX_PENDING) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_PENDING",
                            "You already have the maximum number of pending requests.");
                }
                if (repository.overlaps(listing.id(), start, end, null)) throw datesUnavailable();
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                BigDecimal price = "PAID".equals(listing.pricingType()) ? listing.dailyPrice() : null;
                BigDecimal cost = price == null ? BigDecimal.ZERO.setScale(2) : price.multiply(BigDecimal.valueOf(days));
                return repository.insert(listing.id(), borrowerId, start, end, listing.pricingType(), price, cost, note,
                        Instant.now().plus(PENDING_LIFETIME));
            });
        } catch (DataIntegrityViolationException exception) {
            if (isExclusionViolation(exception)) throw datesUnavailable();
            throw exception;
        }
        log.info("Reservation requested, reservationId={}, listingId={}", id, request.listingId());
        return detail(principal, id.toString());
    }

    public ReservationDetail detail(AuthPrincipal principal, String idValue) {
        repository.expireDue(today());
        return toDetail(participantRow(principal, parse(idValue)), principal.user().id());
    }

    public PageResponse<ReservationSummary> list(AuthPrincipal principal, String role, String status, int page, int size) {
        if (!"borrower".equals(role) && !"owner".equals(role)) throw invalid("role");
        if (status != null && !List.of("PENDING", "ACCEPTED", "REJECTED", "CANCELLED", "EXPIRED", "ACTIVE", "COMPLETED").contains(status)) {
            throw invalid("status");
        }
        if (page < 0 || page > 10_000 || size < 1 || size > 50) throw invalid("page");
        repository.expireDue(today());
        UUID userId = principal.user().id();
        boolean owner = "owner".equals(role);
        List<ReservationRow> rows = owner ? repository.findForOwner(userId, status, size, (long) page * size)
                : repository.findForBorrower(userId, status, size, (long) page * size);
        long total = owner ? repository.countForOwner(userId, status) : repository.countForBorrower(userId, status);
        List<ReservationSummary> items = rows.stream().map(row -> summary(row, userId)).toList();
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public ReservationDetail act(AuthPrincipal principal, String idValue, String action, String reasonValue) {
        UUID id = parse(idValue);
        UUID userId = principal.user().id();
        String reason = reasonValue == null || reasonValue.isBlank() ? null : reasonValue.trim();
        LocalDate today = today();
        transactions.executeWithoutResult(status -> {
            repository.expireDue(today);
            ReservationRow initial = participantRow(principal, id);
            listings.lockById(initial.listingId());
            ReservationRow row = participantRow(principal, id);
            boolean isOwner = row.ownerId().equals(userId);
            if (!allowedActions(row, userId, today).contains(action)) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "This action is not available for the reservation.");
            }
            switch (action) {
                case "ACCEPT" -> {
                    if (repository.overlaps(row.listingId(), row.startDate(), row.endDate(), row.id())) throw datesUnavailable();
                    repository.transition(id, "ACCEPTED", "decided_at", null, null);
                }
                case "REJECT" -> repository.transition(id, "REJECTED", "decided_at", reason, null);
                case "CANCEL" -> {
                    if (isOwner && reason == null) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REASON_REQUIRED",
                                "A reason is required when the owner cancels.", Map.of("reason", List.of("must not be blank")));
                    }
                    repository.transition(id, "CANCELLED", "cancelled_at", reason, isOwner ? "OWNER" : "BORROWER");
                }
                case "HANDOVER" -> repository.transition(id, "ACTIVE", "handed_over_at", null, null);
                case "RETURN" -> repository.transition(id, "COMPLETED", "returned_at", null, null);
                default -> throw new IllegalArgumentException("Unknown action.");
            }
        });
        log.info("Reservation transition, reservationId={}, action={}", id, action);
        return detail(principal, idValue);
    }

    public List<DateRange> availability(String listingIdValue, LocalDate from, LocalDate to) {
        UUID listingId = parse(listingIdValue);
        LocalDate today = today();
        LocalDate start = from == null || from.isBefore(today) ? today : from;
        LocalDate end = to == null ? start.plusDays(MAX_DAYS_AHEAD) : to;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 190) throw invalid("to");
        ListingRow listing = listings.findById(listingId).orElse(null);
        if (listing == null || !"ACTIVE".equals(listing.status())) throw notFound();
        repository.expireDue(today);
        return merge(repository.busyRanges(listingId, start, end));
    }

    public List<Block> blocks(AuthPrincipal principal, String listingIdValue) {
        ListingRow listing = ownedListing(principal, parse(listingIdValue), false);
        return repository.blocks(listing.id(), today());
    }

    public Block addBlock(AuthPrincipal principal, String listingIdValue, BlockRequest request) {
        UUID listingId = parse(listingIdValue);
        LocalDate today = today();
        if (request.endDate().isBefore(request.startDate())) throw invalid("endDate");
        if (request.startDate().isBefore(today)) throw invalid("startDate");
        if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1 > MAX_BLOCK_DAYS) throw invalid("endDate");
        try {
            return transactions.execute(status -> {
                repository.expireDue(today);
                ownedListing(principal, listingId, true);
                if (repository.overlapsReservations(listingId, request.startDate(), request.endDate())) {
                    throw new ApiException(HttpStatus.CONFLICT, "DATES_RESERVED",
                            "The period overlaps a pending or accepted reservation.");
                }
                if (repository.overlapsBlock(listingId, request.startDate(), request.endDate())) {
                    throw new ApiException(HttpStatus.CONFLICT, "BLOCK_OVERLAP", "The period overlaps an existing block.");
                }
                return repository.insertBlock(listingId, request.startDate(), request.endDate());
            });
        } catch (DataIntegrityViolationException exception) {
            if (isExclusionViolation(exception)) {
                throw new ApiException(HttpStatus.CONFLICT, "BLOCK_OVERLAP", "The period overlaps an existing block.");
            }
            throw exception;
        }
    }

    public void deleteBlock(AuthPrincipal principal, String listingIdValue, String blockIdValue) {
        UUID listingId = parse(listingIdValue);
        UUID blockId = parse(blockIdValue);
        transactions.executeWithoutResult(status -> {
            ownedListing(principal, listingId, true);
            if (!repository.deleteBlock(listingId, blockId)) throw notFound();
        });
    }

    /** Called by the listing archive flow inside its transaction, with the listing row already locked. */
    public void prepareArchive(UUID listingId) {
        repository.expireDue(today());
        if (repository.countAcceptedOrActive(listingId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LISTING_HAS_RESERVATIONS",
                    "The listing has accepted or ongoing reservations.");
        }
        int rejected = repository.rejectPending(listingId, "Ogłoszenie zostało wycofane.");
        if (rejected > 0) log.info("Pending reservations rejected on archive, listingId={}, count={}", listingId, rejected);
    }

    List<String> allowedActions(ReservationRow row, UUID userId, LocalDate today) {
        boolean owner = row.ownerId().equals(userId);
        List<String> actions = new ArrayList<>();
        switch (row.status()) {
            case "PENDING" -> {
                if (owner) { actions.add("ACCEPT"); actions.add("REJECT"); }
                else actions.add("CANCEL");
            }
            case "ACCEPTED" -> {
                actions.add("CANCEL");
                if (owner && !today.isBefore(row.startDate().minusDays(1))) actions.add("HANDOVER");
            }
            case "ACTIVE" -> { if (owner) actions.add("RETURN"); }
            default -> { }
        }
        return actions;
    }

    private ListingRow ownedListing(AuthPrincipal principal, UUID listingId, boolean lock) {
        ListingRow listing = (lock ? listings.lockById(listingId) : listings.findById(listingId)).orElse(null);
        if (listing == null || !listing.ownerId().equals(principal.user().id()) || "HIDDEN".equals(listing.status())) {
            throw notFound();
        }
        return listing;
    }

    private ReservationRow participantRow(AuthPrincipal principal, UUID id) {
        UUID userId = principal.user().id();
        ReservationRow row = repository.findById(id).orElseThrow(this::notFound);
        if (!row.borrowerId().equals(userId) && !row.ownerId().equals(userId)) throw notFound();
        return row;
    }

    private ReservationDetail toDetail(ReservationRow row, UUID userId) {
        boolean owner = row.ownerId().equals(userId);
        boolean revealHandover = "ACCEPTED".equals(row.status()) || "ACTIVE".equals(row.status());
        return new ReservationDetail(row.id(), owner ? "OWNER" : "BORROWER", row.status(), row.startDate(), row.endDate(),
                days(row), row.pricingType(), row.dailyPrice(), row.estimatedCost(), row.currency(), row.note(),
                row.decisionReason(), row.cancelledBy(), listingRef(row), counterpart(row, owner),
                row.publicLat(), row.publicLng(), revealHandover ? row.handoverLat() : null,
                revealHandover ? row.handoverLng() : null, allowedActions(row, userId, today()), row.expiresAt(),
                row.createdAt(), row.decidedAt(), row.handedOverAt(), row.returnedAt(), row.cancelledAt());
    }

    private ReservationSummary summary(ReservationRow row, UUID userId) {
        boolean owner = row.ownerId().equals(userId);
        return new ReservationSummary(row.id(), owner ? "OWNER" : "BORROWER", row.status(), row.startDate(), row.endDate(),
                days(row), row.pricingType(), row.estimatedCost(), row.currency(), listingRef(row), counterpart(row, owner),
                row.expiresAt(), row.createdAt());
    }

    private ListingRef listingRef(ReservationRow row) {
        return new ListingRef(row.listingId(), row.listingTitle(),
                row.coverImageId() == null ? null : "/api/images/" + row.coverImageId(), row.listingStatus());
    }

    private Counterpart counterpart(ReservationRow row, boolean viewerIsOwner) {
        return viewerIsOwner ? new Counterpart(row.borrowerId(), row.borrowerName(), row.borrowerArea())
                : new Counterpart(row.ownerId(), row.ownerName(), row.ownerArea());
    }

    private int days(ReservationRow row) { return (int) ChronoUnit.DAYS.between(row.startDate(), row.endDate()) + 1; }

    static List<DateRange> merge(List<DateRange> ranges) {
        List<DateRange> merged = new ArrayList<>();
        for (DateRange range : ranges) {
            if (!merged.isEmpty()) {
                DateRange last = merged.get(merged.size() - 1);
                if (!range.startDate().isAfter(last.endDate().plusDays(1))) {
                    LocalDate end = range.endDate().isAfter(last.endDate()) ? range.endDate() : last.endDate();
                    merged.set(merged.size() - 1, new DateRange(last.startDate(), end));
                    continue;
                }
            }
            merged.add(range);
        }
        return merged;
    }

    private boolean isExclusionViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private UUID parse(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw notFound(); }
    }

    private ApiException datesUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "DATES_UNAVAILABLE", "The selected dates are no longer available.");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "The resource was not found.");
    }

    private ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                Map.of(field, List.of("is invalid")));
    }
}
