package pl.lendly.auth;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.AuthDtos.CodeRequest;
import pl.lendly.auth.AuthDtos.PrivateProfile;

@RestController
@RequestMapping("/api/auth/email")
public class EmailVerificationController {
    private final EmailVerificationService verification;
    private final AuthService auth;

    public EmailVerificationController(EmailVerificationService verification, AuthService auth) {
        this.verification = verification;
        this.auth = auth;
    }

    @PostMapping("/verify")
    ResponseEntity<PrivateProfile> verify(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody CodeRequest body) {
        verification.verify(principal, body.code());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.profile(auth.current(principal)));
    }

    @PostMapping("/resend")
    ResponseEntity<Void> resend(@AuthenticationPrincipal AuthPrincipal principal) {
        verification.resend(principal);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
