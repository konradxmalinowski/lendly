package pl.lendly.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.MfaService;
import pl.lendly.config.AuthProperties;
import pl.lendly.mail.MailService;

@RestController
public class HealthController {
    private final AuthProperties auth;
    private final MailService mail;
    private final MfaService mfa;

    public HealthController(AuthProperties auth, MailService mail, MfaService mfa) {
        this.auth = auth;
        this.mail = mail;
        this.mfa = mfa;
    }

    /** {@code demo} tells the UI to label the instance, so demo accounts and data are never mistaken for real ones. */
    @GetMapping("/api/health")
    HealthResponse health() {
        return new HealthResponse("UP", auth.registrationOpen(), new Features(
                auth.registrationAllowed(mail.available()), auth.googleConfigured(), mfa.available()));
    }

    record HealthResponse(String status, boolean demo, Features features) {
    }

    record Features(boolean registration, boolean googleSignIn, boolean mfa) {
    }
}
