package pl.lendly.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.lendly.common.api.ApiError;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class AccessCookieFilter extends OncePerRequestFilter {
    private final AuthCookieSupport cookies;
    private final AuthTokenService tokens;
    private final AuthRepository repository;
    private final ObjectMapper mapper;

    public AccessCookieFilter(AuthCookieSupport cookies, AuthTokenService tokens, AuthRepository repository, ObjectMapper mapper) {
        this.cookies = cookies;
        this.tokens = tokens;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookies.read(request, cookies.accessName());
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthTokenService.TokenClaims claims = tokens.verify(token);
                var userOptional = repository.activeUserForSession(claims.sessionId(), claims.userId());
                if (userOptional.isPresent()) {
                    AuthUser user = userOptional.get();
                    if (!"ACTIVE".equals(user.status()) && requiresActiveAccount(request)) {
                        response.setStatus(403);
                        response.setContentType("application/json");
                        response.setHeader("Cache-Control", "no-store");
                        Object correlation = request.getAttribute("correlationId");
                        mapper.writeValue(response.getOutputStream(), new ApiError("ACCOUNT_SUSPENDED",
                                "This account cannot access the service.", null,
                                correlation instanceof String id ? id : "unavailable"));
                        return;
                    }
                    AuthPrincipal principal = new AuthPrincipal(user, claims.sessionId());
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean requiresActiveAccount(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equals(method) && ("/api/health".equals(path) || "/api/auth/csrf".equals(path)
                || path.startsWith("/api/users/") || "/api/categories".equals(path) || "/api/items".equals(path)
                || path.startsWith("/api/items/") || path.startsWith("/api/images/") || "/api/ai/status".equals(path))) return false;
        if ("POST".equals(method) && Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                "/api/auth/logout", "/api/auth/google", "/api/auth/mfa/verify").contains(path)) return false;
        return !"GET".equals(method) || !"/api/auth/google/callback".equals(path);
    }
}
