package pl.lendly.moderation;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import pl.lendly.moderation.ModerationDtos.OutcomeRequest;
import pl.lendly.moderation.ModerationDtos.ReasonRequest;
import pl.lendly.moderation.ModerationDtos.ReportCreated;
import pl.lendly.moderation.ModerationDtos.ReportDetail;
import pl.lendly.moderation.ModerationDtos.ReportRequest;
import pl.lendly.moderation.ModerationDtos.ReportSummary;

@RestController
@RequestMapping("/api")
public class ModerationController {
    private final ModerationService moderation;
    private final RateLimiter rateLimiter;

    public ModerationController(ModerationService moderation, RateLimiter rateLimiter) {
        this.moderation = moderation;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/reports")
    ResponseEntity<ReportCreated> report(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody ReportRequest request) {
        rateLimiter.reportCreate(principal.user().id());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(moderation.report(principal, request));
    }

    @GetMapping("/moderation/reports")
    ResponseEntity<PageResponse<ReportSummary>> queue(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @RequestParam(defaultValue = "OPEN") String state,
                                                      @RequestParam(required = false) String targetType,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.queue(principal, state, targetType, page, size));
    }

    @GetMapping("/moderation/reports/{id}")
    ResponseEntity<ReportDetail> detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.detail(principal, id));
    }

    @PostMapping("/moderation/reports/{id}/resolve")
    ResponseEntity<ReportDetail> resolve(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                         @Valid @RequestBody OutcomeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(moderation.resolve(principal, id, request));
    }

    @PostMapping("/moderation/listings/{id}/hide")
    ResponseEntity<Void> hide(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                              @Valid @RequestBody ReasonRequest request) {
        moderation.hideListing(principal, id, request.reason(), request.reportId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moderation/listings/{id}/restore")
    ResponseEntity<Void> restore(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                 @Valid @RequestBody ReasonRequest request) {
        moderation.restoreListing(principal, id, request.reason(), request.reportId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moderation/users/{id}/suspend")
    ResponseEntity<Void> suspend(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                 @Valid @RequestBody ReasonRequest request) {
        moderation.suspendUser(principal, id, request.reason(), request.reportId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moderation/users/{id}/reinstate")
    ResponseEntity<Void> reinstate(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String id,
                                   @Valid @RequestBody ReasonRequest request) {
        moderation.reinstateUser(principal, id, request.reason(), request.reportId());
        return ResponseEntity.noContent().build();
    }
}
