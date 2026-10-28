package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.JwtSecret;
import pl.lendly.config.RateLimitProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private final ClientAddressResolver addresses;
    private final AuthProperties properties;
    private final RateLimitProperties limits;
    private final JdbcTemplate jdbc;
    private final byte[] bucketKey;

    public RateLimiter(ClientAddressResolver addresses, AuthProperties properties, RateLimitProperties limits, JdbcTemplate jdbc) {
        this.addresses = addresses;
        this.properties = properties;
        this.limits = limits;
        this.jdbc = jdbc;
        this.bucketKey = deriveBucketKey();
    }

    public void registration(HttpServletRequest request) {
        hit("register:" + addresses.resolve(request), limits.getRegistration(), Duration.ofSeconds(limits.getRegistrationWindowSeconds()));
    }

    public void login(HttpServletRequest request, String normalizedEmail) {
        String ip = addresses.resolve(request);
        Duration window = Duration.ofSeconds(limits.getLoginWindowSeconds());
        hit("login-ip:" + ip, limits.getLoginIp(), window);
        hit("login-email:" + ip + ":" + hmac(normalizedEmail), limits.getLoginEmail(), window);
        hit("login-wide:" + ip, limits.getLoginWideIp(), window);
    }

    public void refresh(HttpServletRequest request) { hit("refresh:" + addresses.resolve(request), limits.getRefresh(), Duration.ofSeconds(limits.getRefreshWindowSeconds())); }
    public void google(HttpServletRequest request) { hit("google:" + addresses.resolve(request), limits.getGoogle(), Duration.ofSeconds(limits.getGoogleWindowSeconds())); }
    public void callback(HttpServletRequest request) { hit("callback:" + addresses.resolve(request), limits.getCallback(), Duration.ofSeconds(limits.getCallbackWindowSeconds())); }
    public void link(HttpServletRequest request) { hit("link:" + addresses.resolve(request), limits.getLink(), Duration.ofSeconds(limits.getLinkWindowSeconds())); }

    public void listingCreate(java.util.UUID userId) {
        hit("listing-create:" + userId, limits.getListingCreate(), Duration.ofSeconds(limits.getListingCreateWindowSeconds()));
    }

    public void imageUpload(java.util.UUID userId) {
        hit("image-upload:" + userId, limits.getImageUpload(), Duration.ofSeconds(limits.getImageUploadWindowSeconds()));
    }

    public void reservationCreate(java.util.UUID userId) {
        hit("reservation-create:" + userId, limits.getReservationCreate(), Duration.ofSeconds(limits.getReservationCreateWindowSeconds()));
    }

    public void reportCreate(java.util.UUID userId) {
        hit("report-create:" + userId, limits.getReportCreate(), Duration.ofSeconds(limits.getReportCreateWindowSeconds()));
    }

    public void aiScan(java.util.UUID userId) {
        hit("ai-scan:" + userId, limits.getAiScan(), Duration.ofSeconds(limits.getAiScanWindowSeconds()));
    }

    public void mission(java.util.UUID userId) {
        hit("mission:" + userId, limits.getMission(), Duration.ofSeconds(limits.getMissionWindowSeconds()));
    }

    public void emailVerify(java.util.UUID userId) {
        hit("email-verify:" + userId, limits.getEmailVerify(), Duration.ofSeconds(limits.getEmailVerifyWindowSeconds()));
    }

    public void emailResend(java.util.UUID userId) {
        hit("email-resend:" + userId, limits.getEmailResend(), Duration.ofSeconds(limits.getEmailResendWindowSeconds()));
    }

    public void mfaVerify(HttpServletRequest request) {
        hit("mfa-verify:" + addresses.resolve(request), limits.getMfaVerify(), Duration.ofSeconds(limits.getMfaVerifyWindowSeconds()));
    }

    public void mfaVerifyUser(java.util.UUID userId) {
        hit("mfa-verify:user:" + userId, limits.getMfaVerify() * 3, Duration.ofSeconds(limits.getMfaVerifyWindowSeconds()));
    }

    public void mfaManage(java.util.UUID userId) {
        hit("mfa-manage:" + userId, limits.getMfaManage(), Duration.ofSeconds(limits.getMfaManageWindowSeconds()));
    }

    public void accountDelete(java.util.UUID userId) {
        hit("account-delete:" + userId, limits.getAccountDelete(), Duration.ofSeconds(limits.getAccountDeleteWindowSeconds()));
    }

    public void dataExport(java.util.UUID userId) {
        hit("data-export:" + userId, limits.getDataExport(), Duration.ofSeconds(limits.getDataExportWindowSeconds()));
    }

    public void search(HttpServletRequest request) {
        hit("search:" + addresses.resolve(request), limits.getSearch(), Duration.ofSeconds(limits.getSearchWindowSeconds()), true);
    }

    public void csrf(HttpServletRequest request) {
        Duration window = Duration.ofSeconds(limits.getCsrfWindowSeconds());
        hit("csrf-client:" + addresses.resolve(request), limits.getCsrfPerClient(), window);
        hit("csrf-global", limits.getCsrfGlobal(), window);
    }

    private void hit(String key, int limit, Duration window) { hit(key, limit, window, false); }

    // Single atomic upsert on the database clock. Called outside business transactions so a rollback never refunds a hit.
    private void hit(String key, int limit, Duration window, boolean failOpen) {
        try {
            long seconds = window.toSeconds();
            long[] result = jdbc.queryForObject("""
                    INSERT INTO rate_limit_buckets AS b (bucket_key, hit_count, expires_at)
                    VALUES (?, 1, CURRENT_TIMESTAMP + ? * INTERVAL '1 second')
                    ON CONFLICT (bucket_key) DO UPDATE SET
                        hit_count = CASE WHEN b.expires_at <= CURRENT_TIMESTAMP THEN 1 ELSE b.hit_count + 1 END,
                        expires_at = CASE WHEN b.expires_at <= CURRENT_TIMESTAMP THEN EXCLUDED.expires_at ELSE b.expires_at END
                    RETURNING b.hit_count, GREATEST(1, CEIL(EXTRACT(EPOCH FROM (b.expires_at - CURRENT_TIMESTAMP))))::bigint
                    """, (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)}, bucketKey(key, seconds), seconds);
            if (result != null && result[0] > limit) throw limited(result[1]);
        } catch (DataAccessException exception) {
            if (failOpen) {
                log.warn("Rate limiter unavailable, allowing request: {}", exception.getClass().getSimpleName());
                return;
            }
            log.error("Rate limiter unavailable, rejecting request: {}", exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "The service is temporarily unavailable.");
        }
    }

    private byte[] bucketKey(String key, long windowSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bucketKey, "HmacSHA256"));
            return mac.doFinal((key + "|" + windowSeconds).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Stable across restarts: derived with a fixed label from the rate-limit HMAC key, else from the JWT secret.
    private byte[] deriveBucketKey() {
        byte[] master = null;
        String hmacKey = properties.getRateLimitHmacKey();
        if (hmacKey.getBytes(StandardCharsets.UTF_8).length >= 32) {
            master = hmacKey.getBytes(StandardCharsets.UTF_8);
        } else if (!properties.getJwtSecret().isBlank()) {
            try { master = JwtSecret.decode(properties.getJwtSecret()); }
            catch (IllegalArgumentException ignored) { }
        }
        if (master == null) {
            log.warn("No rate-limit HMAC key or JWT secret configured: limiter keys are random and reset on restart.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(master, "HmacSHA256"));
            return derive.doFinal("lendly:rate-limit-bucket:v1".getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictExpiredBuckets() {
        try {
            jdbc.update("DELETE FROM rate_limit_buckets WHERE expires_at <= CURRENT_TIMESTAMP");
        } catch (DataAccessException exception) {
            log.warn("Rate limit cleanup failed: {}", exception.getClass().getSimpleName());
        }
    }

    private ApiException limited(long seconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests.", seconds);
    }

    private String hmac(String value) {
        try {
            if (properties.getRateLimitHmacKey().length() < 32) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Authentication is not configured.");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getRateLimitHmacKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
