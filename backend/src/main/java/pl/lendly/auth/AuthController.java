package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import pl.lendly.auth.AuthDtos.*;
import pl.lendly.common.api.ApiException;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CsrfService csrf;
    private final SessionService sessions;
    private final AuthCookieSupport cookies;
    private final RateLimiter limiter;
    private final GoogleOidcService google;

    public AuthController(AuthService auth, CsrfService csrf, SessionService sessions,
                          AuthCookieSupport cookies, RateLimiter limiter, GoogleOidcService google) {
        this.auth = auth;
        this.csrf = csrf;
        this.sessions = sessions;
        this.cookies = cookies;
        this.limiter = limiter;
        this.google = google;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(HttpServletRequest request, HttpServletResponse response,
                                      @AuthenticationPrincipal AuthPrincipal principal) {
        limiter.csrf(request);
        String refresh = cookies.read(request, cookies.refreshName());
        String token = principal != null ? csrf.issueForSession(principal.sessionId())
                : refresh != null ? csrf.issueForRefresh(refresh) : null;
        if (token == null) token = csrf.issueAnonymous(response);
        if (principal != null) cookies.clearContext(response);
        else if (refresh != null) cookies.clearContext(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CsrfResponse(token));
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
                                          HttpServletResponse response) {
        limiter.registration(request);
        AuthService.LoginResult result = auth.register(body.displayName(), body.email(), body.password());
        establish(response, result);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new AuthResponse(result.user().privateProfile(), result.csrfToken()));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                       HttpServletResponse response) {
        limiter.login(request, body.email().trim().toLowerCase(java.util.Locale.ROOT));
        AuthService.LoginResult result = auth.login(body.email(), body.password());
        establish(response, result);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AuthResponse(result.user().privateProfile(), result.csrfToken()));
    }

    @PostMapping("/refresh")
    ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        limiter.refresh(request);
        String raw = cookies.read(request, cookies.refreshName());
        SessionService.Rotation rotation = sessions.rotate(raw);
        if (rotation.invalid()) {
            cookies.clearAuth(response);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "The session is no longer valid.");
        }
        issueCookies(response, rotation.issued());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, @AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse response) {
        if (principal != null) {
            sessions.revoke(principal.sessionId());
        } else sessions.revokeByRefreshHash(CsrfService.hash(cookies.read(request, cookies.refreshName())));
        cookies.clearAuth(response);
        cookies.clearContext(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/google")
    ResponseEntity<GoogleUrlResponse> google(HttpServletRequest request) {
        limiter.google(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new GoogleUrlResponse(google.startSignIn(cookies.read(request, cookies.csrfName()))));
    }

    @PostMapping("/google/link")
    ResponseEntity<GoogleUrlResponse> googleLink(HttpServletRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        limiter.link(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new GoogleUrlResponse(google.startLink(principal)));
    }

    @GetMapping("/google/callback")
    RedirectView googleCallback(@RequestParam(required = false) String state,
                                @RequestParam(required = false) String code,
                                @RequestParam(required = false) String error,
                                @AuthenticationPrincipal AuthPrincipal principal,
                                HttpServletRequest request, HttpServletResponse response) {
        limiter.callback(request);
        GoogleOidcService.CallbackResult result = google.complete(state, code, error, principal,
                cookies.read(request, cookies.csrfName()));
        switch (result.kind()) {
            case SIGNED_IN -> {
                String csrfToken = csrf.issuePendingSessionToken();
                issueCookies(response, sessions.create(result.user().id(), csrfToken));
                cookies.clearContext(response);
                return redirect("/");
            }
            case LINKED -> { return redirect("/profile?google=linked"); }
            case LINK_REQUIRED -> { return redirect("/login?auth=link-required"); }
            case FAILURE -> { return redirect(result.linkIntent() ? "/profile?google=link-failed" : "/login?auth=failed"); }
            default -> { return redirect("/login?auth=failed"); }
        }
    }

    private RedirectView redirect(String path) {
        RedirectView redirect = new RedirectView(path);
        redirect.setExposeModelAttributes(false);
        redirect.setStatusCode(org.springframework.http.HttpStatus.FOUND);
        return redirect;
    }

    private void establish(HttpServletResponse response, AuthService.LoginResult result) {
        issueCookies(response, result.session());
        cookies.clearContext(response);
    }

    private void issueCookies(HttpServletResponse response, SessionService.IssuedSession session) {
        cookies.setAccess(response, session.accessToken(), session.accessLifetime());
        cookies.setRefresh(response, session.refreshToken(), session.refreshLifetime());
    }
}
