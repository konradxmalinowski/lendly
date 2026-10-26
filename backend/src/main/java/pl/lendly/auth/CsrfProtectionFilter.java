package pl.lendly.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lendly.common.api.ApiError;
import pl.lendly.common.api.ApiException;

import java.io.IOException;
import java.util.Set;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> ANONYMOUS_CSRF_PATHS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/google");
    private final CsrfService csrf;
    private final AuthCookieSupport cookies;
    private final ObjectMapper mapper;

    public CsrfProtectionFilter(CsrfService csrf, AuthCookieSupport cookies, ObjectMapper mapper) {
        this.csrf = csrf;
        this.cookies = cookies;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!unsafe(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String path = request.getRequestURI();
            AuthPrincipal principal = currentPrincipal();
            if (ANONYMOUS_CSRF_PATHS.contains(path)) {
                csrf.consumeAnonymous(request);
            } else if ("/api/auth/refresh".equals(path)) {
                csrf.verifyRefresh(request, CsrfService.hash(cookies.read(request, cookies.refreshName())));
            } else if ("/api/auth/logout".equals(path)) {
                if (principal != null) csrf.verifySession(request, principal);
                else if (cookies.read(request, cookies.refreshName()) != null) {
                    try { csrf.verifyRefresh(request, CsrfService.hash(cookies.read(request, cookies.refreshName()))); }
                    catch (ApiException exception) {
                        if (exception.status() != HttpStatus.UNAUTHORIZED) throw exception;
                        csrf.verifyOrigin(request);
                    }
                } else csrf.verifyOrigin(request);
            } else if (principal != null) {
                csrf.verifySession(request, principal);
            } else {
                csrf.verifyOrigin(request);
            }
            chain.doFilter(request, response);
        } catch (ApiException exception) {
            if (exception.status() == HttpStatus.UNAUTHORIZED) cookies.clearAuth(response);
            response.setStatus(exception.status().value());
            if (exception.retryAfterSeconds() != null) response.setHeader("Retry-After", exception.retryAfterSeconds().toString());
            response.setContentType("application/json");
            Object correlation = request.getAttribute("correlationId");
            mapper.writeValue(response.getOutputStream(), new ApiError(exception.code(), exception.getMessage(), null,
                    correlation instanceof String id ? id : "unavailable"));
        }
    }

    private AuthPrincipal currentPrincipal() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal : null;
    }

    private boolean unsafe(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method) && !"TRACE".equals(method);
    }
}
