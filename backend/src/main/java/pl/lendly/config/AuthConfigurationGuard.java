package pl.lendly.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class AuthConfigurationGuard implements ApplicationRunner {
    private final AuthProperties properties;
    private final RateLimitProperties limits;
    private final Environment environment;
    private final MailProperties mail;

    public AuthConfigurationGuard(AuthProperties properties, RateLimitProperties limits, Environment environment,
                                  MailProperties mail) {
        this.properties = properties;
        this.limits = limits;
        this.environment = environment;
        this.mail = mail;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = environment.matchesProfiles("prod", "production");
        boolean localProfile = environment.matchesProfiles("local");
        if (limits.getRegistration() < 1 || limits.getRegistrationWindowSeconds() < 1
                || limits.getLoginIp() < 1 || limits.getLoginEmail() < 1 || limits.getLoginWideIp() < 1
                || limits.getLoginWindowSeconds() < 1 || limits.getRefresh() < 1 || limits.getRefreshWindowSeconds() < 1
                || limits.getGoogle() < 1 || limits.getGoogleWindowSeconds() < 1 || limits.getCallback() < 1
                || limits.getCallbackWindowSeconds() < 1 || limits.getLink() < 1 || limits.getLinkWindowSeconds() < 1
                || limits.getCsrfPerClient() < 1 || limits.getCsrfGlobal() < 1 || limits.getCsrfWindowSeconds() < 1
                || limits.getListingCreate() < 1 || limits.getListingCreateWindowSeconds() < 1
                || limits.getImageUpload() < 1 || limits.getImageUploadWindowSeconds() < 1
                || limits.getSearch() < 1 || limits.getSearchWindowSeconds() < 1
                || limits.getReservationCreate() < 1 || limits.getReservationCreateWindowSeconds() < 1
                || limits.getReportCreate() < 1 || limits.getReportCreateWindowSeconds() < 1
                || limits.getAiScan() < 1 || limits.getAiScanWindowSeconds() < 1
                || limits.getMission() < 1 || limits.getMissionWindowSeconds() < 1
                || limits.getEmailVerify() < 1 || limits.getEmailVerifyWindowSeconds() < 1
                || limits.getEmailResend() < 1 || limits.getEmailResendWindowSeconds() < 1
                || limits.getMfaVerify() < 1 || limits.getMfaVerifyWindowSeconds() < 1
                || limits.getMfaManage() < 1 || limits.getMfaManageWindowSeconds() < 1) {
            throw new IllegalStateException("All rate limits and windows must be positive.");
        }
        URI publicOrigin = properties.getPublicOrigin().isBlank() ? null : parse(properties.getPublicOrigin());
        boolean httpsOrigin = publicOrigin != null && "https".equalsIgnoreCase(publicOrigin.getScheme());
        if (httpsOrigin && !properties.isSecureCookies()) {
            throw new IllegalStateException("HTTPS origins require secure auth cookies.");
        }
        if (!properties.isSecureCookies() && (!localProfile || !isLoopbackHttpOrigin(publicOrigin))) {
            throw new IllegalStateException("Secure cookies may be disabled only with the local profile and an HTTP localhost origin.");
        }
        if (production) {
            if (!properties.isSecureCookies()
                    || properties.getRateLimitHmacKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Production auth requires secure cookies and configured signing keys.");
            }
            if (!httpsOrigin || publicOrigin.getHost() == null || publicOrigin.getRawPath() != null && !publicOrigin.getRawPath().isEmpty()
                    || publicOrigin.getRawQuery() != null || publicOrigin.getRawFragment() != null || publicOrigin.getUserInfo() != null
                    || !properties.getAllowedOrigins().contains(properties.getPublicOrigin())) {
                throw new IllegalStateException("Production auth requires one exact HTTPS public origin in the allowlist.");
            }
        }
        if (!properties.getJwtSecret().isBlank()) {
            try { JwtSecret.decode(properties.getJwtSecret()); }
            catch (IllegalArgumentException exception) {
                throw new IllegalStateException("JWT signing key must be canonical Base64 encoding at least 32 random bytes.");
            }
        }
        if (production && properties.getJwtSecret().isBlank()) {
            throw new IllegalStateException("Production auth requires a configured JWT signing key.");
        }
        if (!properties.getMfaEncryptionKey().isBlank()) {
            try { MfaEncryptionKey.decode(properties.getMfaEncryptionKey()); }
            catch (IllegalArgumentException exception) {
                throw new IllegalStateException("MFA encryption key must be canonical Base64 encoding exactly 32 random bytes.");
            }
            if (properties.getMfaEncryptionKey().equals(properties.getJwtSecret())) {
                throw new IllegalStateException("MFA encryption key must differ from the JWT signing key.");
            }
        } else if (!localProfile) {
            throw new IllegalStateException("MFA requires LENDLY_MFA_ENCRYPTION_KEY outside the local profile.");
        }
        if (!mail.host().isBlank() && mail.from().isBlank()) {
            throw new IllegalStateException("LENDLY_MAIL_FROM is required when MAIL_HOST is set.");
        }
        if (mail.port() < 1 || mail.port() > 65535) {
            throw new IllegalStateException("MAIL_PORT must be a valid port.");
        }
        boolean anyGoogleValue = !properties.getGoogleClientId().isBlank()
                || !properties.getGoogleClientSecret().isBlank() || !properties.getCallbackUrl().isBlank();
        boolean allGoogleValues = !properties.getGoogleClientId().isBlank()
                && !properties.getGoogleClientSecret().isBlank() && !properties.getCallbackUrl().isBlank();
        if (anyGoogleValue && !allGoogleValues) {
            throw new IllegalStateException("Google OIDC client ID, secret, and callback URL must be configured together.");
        }
        if (allGoogleValues) {
            URI callback = parse(properties.getCallbackUrl());
            URI configuredOrigin = parse(properties.getPublicOrigin());
            if (!"https".equalsIgnoreCase(configuredOrigin.getScheme()) || !"https".equalsIgnoreCase(callback.getScheme())
                    || !callback.getHost().equalsIgnoreCase(configuredOrigin.getHost())
                    || callback.getPort() != configuredOrigin.getPort()
                    || !"/api/auth/google/callback".equals(callback.getPath())) {
                throw new IllegalStateException("Google callback URL must use the configured public HTTPS origin.");
            }
        }
    }

    private URI parse(String value) {
        try { return URI.create(value); }
        catch (Exception exception) { throw new IllegalStateException("Auth URL configuration is invalid."); }
    }

    private boolean isLoopbackHttpOrigin(URI origin) {
        if (origin == null || !"http".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                || origin.getRawPath() != null && !origin.getRawPath().isEmpty()
                || origin.getRawQuery() != null || origin.getRawFragment() != null || origin.getUserInfo() != null) return false;
        String host = origin.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
    }
}
