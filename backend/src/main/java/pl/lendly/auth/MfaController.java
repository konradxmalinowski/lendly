package pl.lendly.auth;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.AuthDtos.CodeRequest;
import pl.lendly.auth.AuthDtos.RecoveryCodesResponse;

@RestController
@RequestMapping("/api/me/mfa")
public class MfaController {
    private final MfaService mfa;
    private final AuthService auth;

    public MfaController(MfaService mfa, AuthService auth) {
        this.mfa = mfa;
        this.auth = auth;
    }

    @GetMapping
    ResponseEntity<MfaService.Status> status(@AuthenticationPrincipal AuthPrincipal principal) {
        return noStore(mfa.status(auth.current(principal)));
    }

    @PostMapping("/setup")
    ResponseEntity<MfaService.Setup> setup(@AuthenticationPrincipal AuthPrincipal principal) {
        auth.current(principal);
        return noStore(mfa.setup(principal));
    }

    @PostMapping("/enable")
    ResponseEntity<RecoveryCodesResponse> enable(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody CodeRequest body) {
        auth.current(principal);
        return noStore(new RecoveryCodesResponse(mfa.enable(principal, body.code())));
    }

    @PostMapping("/recovery-codes")
    ResponseEntity<RecoveryCodesResponse> recoveryCodes(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody CodeRequest body) {
        auth.current(principal);
        return noStore(new RecoveryCodesResponse(mfa.regenerateRecoveryCodes(principal, body.code())));
    }

    @PostMapping("/disable")
    ResponseEntity<Void> disable(@AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody CodeRequest body) {
        auth.current(principal);
        mfa.disable(principal, body.code());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
