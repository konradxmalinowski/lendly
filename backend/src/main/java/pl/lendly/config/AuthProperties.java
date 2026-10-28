package pl.lendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("lendly.auth")
public class AuthProperties {
    private String jwtSecret = "";
    private String issuer = "lendly-api";
    private String audience = "lendly-api";
    private String publicOrigin = "";
    private String callbackUrl = "";
    private String googleClientId = "";
    private String googleClientSecret = "";
    private boolean secureCookies = false;
    private boolean demoRegistration = false;
    private boolean demoRegistrationOnHttps = false;
    private boolean registrationEnabled = false;
    private boolean googleSignupEnabled = false;
    private String mfaEncryptionKey = "";
    private String mfaEncryptionKeyPrevious = "";
    private String rateLimitHmacKey = "";
    private final List<String> trustedProxies = new ArrayList<>();
    private final List<String> allowedOrigins = new ArrayList<>();

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getPublicOrigin() { return publicOrigin; }
    public void setPublicOrigin(String publicOrigin) { this.publicOrigin = publicOrigin; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getGoogleClientId() { return googleClientId; }
    public void setGoogleClientId(String googleClientId) { this.googleClientId = googleClientId; }
    public String getGoogleClientSecret() { return googleClientSecret; }
    public void setGoogleClientSecret(String googleClientSecret) { this.googleClientSecret = googleClientSecret; }
    public boolean isSecureCookies() { return secureCookies; }
    public void setSecureCookies(boolean secureCookies) { this.secureCookies = secureCookies; }
    public boolean isDemoRegistration() { return demoRegistration; }
    public void setDemoRegistration(boolean demoRegistration) { this.demoRegistration = demoRegistration; }
    /** Explicit opt-in for a demo instance served over HTTPS; unverified accounts, never for public production. */
    public boolean isDemoRegistrationOnHttps() { return demoRegistrationOnHttps; }
    public void setDemoRegistrationOnHttps(boolean value) { this.demoRegistrationOnHttps = value; }
    public boolean registrationOpen() {
        return demoRegistration && (!secureCookies || demoRegistrationOnHttps);
    }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean value) { this.registrationEnabled = value; }
    public boolean isGoogleSignupEnabled() { return googleSignupEnabled; }
    public void setGoogleSignupEnabled(boolean value) { this.googleSignupEnabled = value; }
    /** Public or demo registration is allowed only when a mail transport can deliver the verification code. */
    public boolean registrationAllowed(boolean mailAvailable) {
        return mailAvailable && (registrationEnabled || registrationOpen());
    }
    public String getMfaEncryptionKey() { return mfaEncryptionKey; }
    public void setMfaEncryptionKey(String value) { this.mfaEncryptionKey = value; }
    public String getMfaEncryptionKeyPrevious() { return mfaEncryptionKeyPrevious; }
    public void setMfaEncryptionKeyPrevious(String value) { this.mfaEncryptionKeyPrevious = value; }
    public boolean googleConfigured() {
        return !googleClientId.isBlank() && !googleClientSecret.isBlank() && !callbackUrl.isBlank() && secureCookies;
    }
    public String getRateLimitHmacKey() { return rateLimitHmacKey; }
    public void setRateLimitHmacKey(String rateLimitHmacKey) { this.rateLimitHmacKey = rateLimitHmacKey; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
}
