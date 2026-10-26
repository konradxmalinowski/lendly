package pl.lendly.ai;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.lendly.ai.AiDtos.AiStatus;
import pl.lendly.ai.AiDtos.MissionRequest;
import pl.lendly.ai.AiDtos.MissionResult;
import pl.lendly.ai.AiDtos.ScanResult;
import pl.lendly.auth.AuthPrincipal;
import pl.lendly.auth.RateLimiter;
import pl.lendly.common.api.ApiException;
import pl.lendly.storage.ImageProcessor;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiRouter ai;
    private final AiScanService scans;
    private final MissionService missions;
    private final RateLimiter rateLimiter;

    public AiController(AiRouter ai, AiScanService scans, MissionService missions, RateLimiter rateLimiter) {
        this.ai = ai;
        this.scans = scans;
        this.missions = missions;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/status")
    ResponseEntity<AiStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(new AiStatus(ai.client().isAvailable()));
    }

    @PostMapping(path = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ScanResult> scan(@AuthenticationPrincipal AuthPrincipal principal, @RequestPart("file") MultipartFile file)
            throws IOException {
        rateLimiter.aiScan(principal.user().id());
        if (file.isEmpty() || file.getSize() > ImageProcessor.MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "The image must be at most 5 MB.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(scans.scan(file.getBytes()));
    }

    @PostMapping("/missions")
    ResponseEntity<MissionResult> mission(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody MissionRequest request) {
        rateLimiter.mission(principal.user().id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(missions.run(request));
    }
}
