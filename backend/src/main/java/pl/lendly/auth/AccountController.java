package pl.lendly.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.AuthDtos.DeleteAccountRequest;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/me")
public class AccountController {
    private final AccountService accounts;
    private final AuthCookieSupport cookies;

    public AccountController(AccountService accounts, AuthCookieSupport cookies) {
        this.accounts = accounts;
        this.cookies = cookies;
    }

    @PostMapping("/delete")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                @RequestBody(required = false) DeleteAccountRequest body, HttpServletResponse response) {
        accounts.delete(principal, body);
        cookies.clearAuth(response);
        cookies.clearContext(response);
        cookies.clearMfaChallenge(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/export")
    ResponseEntity<AccountDtos.DataExport> export(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"lendly-dane-"
                        + LocalDate.now(java.time.ZoneId.of("Europe/Warsaw")) + ".json\"")
                .body(accounts.export(principal));
    }
}
