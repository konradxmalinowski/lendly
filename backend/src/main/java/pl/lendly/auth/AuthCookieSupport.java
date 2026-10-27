package pl.lendly.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pl.lendly.config.AuthProperties;

import java.time.Duration;

@Component
public class AuthCookieSupport {
    private final AuthProperties properties;

    public AuthCookieSupport(AuthProperties properties) { this.properties = properties; }

    public String accessName() { return properties.isSecureCookies() ? "__Host-lendly_access" : "lendly_access"; }
    public String refreshName() { return properties.isSecureCookies() ? "__Secure-lendly_refresh" : "lendly_refresh"; }
    // __Host- needs Path=/, so a sibling subdomain cannot plant a challenge; the cookie is HttpOnly and lives 5 minutes.
    public String mfaName() { return properties.isSecureCookies() ? "__Host-lendly_mfa" : "lendly_mfa"; }
    public String csrfName() { return properties.isSecureCookies() ? "__Host-lendly_csrf_context" : "lendly_csrf_context"; }

    public String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public void setAccess(HttpServletResponse response, String value, Duration maxAge) {
        set(response, accessName(), value, "/", maxAge, true);
    }
    public void setRefresh(HttpServletResponse response, String value, Duration maxAge) {
        set(response, refreshName(), value, "/api/auth", maxAge, true);
    }
    public void setCsrfContext(HttpServletResponse response, String value, Duration maxAge) {
        set(response, csrfName(), value, "/", maxAge, true);
    }
    public void setMfaChallenge(HttpServletResponse response, String value, Duration maxAge) {
        set(response, mfaName(), value, "/", maxAge, true);
    }
    public void clearMfaChallenge(HttpServletResponse response) { setMfaChallenge(response, "", Duration.ZERO); }
    public void clearAuth(HttpServletResponse response) {
        setAccess(response, "", Duration.ZERO);
        setRefresh(response, "", Duration.ZERO);
    }
    public void clearContext(HttpServletResponse response) { setCsrfContext(response, "", Duration.ZERO); }

    private void set(HttpServletResponse response, String name, String value, String path, Duration age, boolean httpOnly) {
        ResponseCookie cookie = ResponseCookie.from(name, value).httpOnly(httpOnly).secure(properties.isSecureCookies())
                .sameSite("Lax").path(path).maxAge(age).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
