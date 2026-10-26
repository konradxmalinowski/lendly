package pl.lendly.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.config.AuthProperties;

@RestController
public class HealthController {
    private final AuthProperties auth;

    public HealthController(AuthProperties auth) { this.auth = auth; }

    /** {@code demo} tells the UI to label the instance, so demo accounts and data are never mistaken for real ones. */
    @GetMapping("/api/health")
    HealthResponse health() {
        return new HealthResponse("UP", auth.registrationOpen());
    }

    record HealthResponse(String status, boolean demo) {
    }
}
