package pl.lendly.reservations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.auth.RateLimiter;
import pl.lendly.listings.ListingDtos.PageResponse;
import pl.lendly.reservations.ReservationDtos.Block;
import pl.lendly.reservations.ReservationDtos.BlockRequest;
import pl.lendly.reservations.ReservationDtos.DateRange;
import pl.lendly.reservations.ReservationDtos.ReasonRequest;
import pl.lendly.reservations.ReservationDtos.ReservationDetail;
import pl.lendly.reservations.ReservationDtos.ReservationRequest;
import pl.lendly.reservations.ReservationDtos.ReservationSummary;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {
    private final ReservationService reservations;
    private final RateLimiter rateLimiter;

    public ReservationController(ReservationService reservations, RateLimiter rateLimiter) {
        this.reservations = reservations;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/reservations")
    ResponseEntity<ReservationDetail> create(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody ReservationRequest request) {
        rateLimiter.reservationCreate(principal.user().id());
        ReservationDetail created = reservations.create(principal, request);
        return ResponseEntity.created(URI.create("/api/reservations/" + created.id())).cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/reservations")
    ResponseEntity<PageResponse<ReservationSummary>> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @RequestParam(defaultValue = "borrower") String role,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.list(principal, role, status, page, size));
    }

    @GetMapping("/reservations/{id}")
    ResponseEntity<ReservationDetail> detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.detail(principal, id));
    }

    @PostMapping("/reservations/{id}/accept")
    ResponseEntity<ReservationDetail> accept(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return action(principal, id, "ACCEPT", null);
    }

    @PostMapping("/reservations/{id}/reject")
    ResponseEntity<ReservationDetail> reject(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                             @Valid @RequestBody(required = false) ReasonRequest body) {
        return action(principal, id, "REJECT", body == null ? null : body.reason());
    }

    @PostMapping("/reservations/{id}/cancel")
    ResponseEntity<ReservationDetail> cancel(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                             @Valid @RequestBody(required = false) ReasonRequest body) {
        return action(principal, id, "CANCEL", body == null ? null : body.reason());
    }

    @PostMapping("/reservations/{id}/handover")
    ResponseEntity<ReservationDetail> handover(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return action(principal, id, "HANDOVER", null);
    }

    @PostMapping("/reservations/{id}/return")
    ResponseEntity<ReservationDetail> returned(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return action(principal, id, "RETURN", null);
    }

    @GetMapping("/items/{id}/availability")
    ResponseEntity<List<DateRange>> availability(@PathVariable String id, HttpServletRequest request,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        rateLimiter.search(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(reservations.availability(id, from, to));
    }

    @GetMapping("/me/items/{id}/blocks")
    ResponseEntity<List<Block>> blocks(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.blocks(principal, id));
    }

    @PostMapping("/me/items/{id}/blocks")
    ResponseEntity<Block> addBlock(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                   @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(reservations.addBlock(principal, id, request));
    }

    @DeleteMapping("/me/items/{id}/blocks/{blockId}")
    ResponseEntity<Void> deleteBlock(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                     @PathVariable String blockId) {
        reservations.deleteBlock(principal, id, blockId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<ReservationDetail> action(AuthPrincipal principal, String id, String action, String reason) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.act(principal, id, action, reason));
    }
}
