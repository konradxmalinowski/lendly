package pl.lendly.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.lendly.auth.AccessCookieFilter;
import pl.lendly.auth.AccountGateFilter;
import pl.lendly.auth.CsrfProtectionFilter;
import pl.lendly.common.api.CorrelationIdFilter;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({AuthProperties.class, RateLimitProperties.class, StorageProperties.class, AiProperties.class, MailProperties.class})
public class SecurityConfiguration {
    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AccessCookieFilter> accessCookieFilterRegistration(AccessCookieFilter filter) {
        FilterRegistrationBean<AccessCookieFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CsrfProtectionFilter> csrfProtectionFilterRegistration(CsrfProtectionFilter filter) {
        FilterRegistrationBean<CsrfProtectionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<AccountGateFilter> accountGateFilterRegistration(AccountGateFilter filter) {
        FilterRegistrationBean<AccountGateFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessCookieFilter accessCookieFilter,
                                            CsrfProtectionFilter csrfProtectionFilter,
                                            AccountGateFilter accountGateFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http
                // Replaced, not dropped: CsrfProtectionFilter (synchronizer token plus exact Origin check) is added below.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    Object correlation = request.getAttribute("correlationId");
                    objectMapper.writeValue(response.getOutputStream(), new pl.lendly.common.api.ApiError(
                            "FORBIDDEN", "You do not have access to this action.", null,
                            correlation instanceof String id ? id : "unavailable"));
                }).authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    Object correlation = request.getAttribute("correlationId");
                    objectMapper.writeValue(response.getOutputStream(), new pl.lendly.common.api.ApiError(
                            "AUTHENTICATION_REQUIRED", "Authentication is required.", null,
                            correlation instanceof String id ? id : "unavailable"));
                }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf", "/api/auth/google/callback", "/api/users/*",
                                "/api/categories", "/api/items", "/api/items/*", "/api/items/*/availability", "/api/images/*", "/api/ai/status",
                                "/api/seo/items/*/head", "/api/seo/sitemap-listings.xml").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/logout", "/api/auth/google", "/api/auth/mfa/verify").permitAll()
                        .requestMatchers("/api/moderation/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(accessCookieFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfProtectionFilter, AccessCookieFilter.class)
                .addFilterAfter(accountGateFilter, CsrfProtectionFilter.class)
                .build();
    }
}
