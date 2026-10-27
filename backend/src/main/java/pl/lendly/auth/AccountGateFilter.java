package pl.lendly.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lendly.common.api.ApiError;

import java.io.IOException;

/** Runs after CSRF validation: blocks unverified accounts from mutations and moderators without MFA from moderation. */
@Component
public class AccountGateFilter extends OncePerRequestFilter {
    private final MfaService mfa;
    private final ObjectMapper mapper;

    public AccountGateFilter(MfaService mfa, ObjectMapper mapper) {
        this.mfa = mfa;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            AuthUser user = principal.user();
            String path = request.getRequestURI();
            if (path.startsWith("/api/moderation/") && MfaService.required(user.role()) && !mfa.isEnabled(user.id())) {
                reject(request, response, "MFA_SETUP_REQUIRED", "Enable multi-factor authentication to use moderation.");
                return;
            }
            if (unsafe(request.getMethod()) && !user.emailVerified()
                    && !path.startsWith("/api/auth/") && !path.equals("/api/me")) {
                reject(request, response, "EMAIL_NOT_VERIFIED", "Verify your email address first.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        Object correlation = request.getAttribute("correlationId");
        mapper.writeValue(response.getOutputStream(), new ApiError(code, message, null,
                correlation instanceof String id ? id : "unavailable"));
    }

    private boolean unsafe(String method) {
        return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method) && !"TRACE".equals(method);
    }
}
