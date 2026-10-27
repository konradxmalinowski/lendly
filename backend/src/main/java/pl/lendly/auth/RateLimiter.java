package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.RateLimitProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class RateLimiter {
    private static final int BUCKET_COUNT = 65_536;
    private final ClientAddressResolver addresses;
    private final AuthProperties properties;
    private final RateLimitProperties limits;
    // Fixed-size keyed buckets bound memory without turning table saturation into
    // a global deny. Hash collisions can only make the affected slot stricter.
    private final Bucket[][] buckets = new Bucket[21][BUCKET_COUNT];
    private final byte[] bucketKey = new byte[32];

    public RateLimiter(ClientAddressResolver addresses, AuthProperties properties, RateLimitProperties limits) {
        this.addresses = addresses;
        this.properties = properties;
        this.limits = limits;
        new SecureRandom().nextBytes(bucketKey);
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

    public void search(HttpServletRequest request) {
        hit("search:" + addresses.resolve(request), limits.getSearch(), Duration.ofSeconds(limits.getSearchWindowSeconds()));
    }

    public void csrf(HttpServletRequest request) {
        Duration window = Duration.ofSeconds(limits.getCsrfWindowSeconds());
        hit("csrf-client:" + addresses.resolve(request), limits.getCsrfPerClient(), window);
        hit("csrf-global", limits.getCsrfGlobal(), window);
    }

    private synchronized void hit(String key, int limit, Duration window) {
        Instant now = Instant.now();
        int scope = scope(key);
        int slot = slot(key);
        Bucket bucket = buckets[scope][slot];
        if (bucket == null || !now.isBefore(bucket.expiresAt)) {
            bucket = new Bucket(now.plus(window), 0);
            buckets[scope][slot] = bucket;
        }
        bucket.count++;
        if (bucket.count > limit) {
            long seconds = Math.max(1, Duration.between(now, bucket.expiresAt).toSeconds());
            throw limited(seconds);
        }
    }

    private int slot(String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bucketKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(key.getBytes(StandardCharsets.UTF_8));
            return Math.floorMod(java.nio.ByteBuffer.wrap(digest).getInt(), BUCKET_COUNT);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int scope(String key) {
        if (key.startsWith("register:")) return 0;
        if (key.startsWith("login-ip:")) return 1;
        if (key.startsWith("login-email:")) return 2;
        if (key.startsWith("login-wide:")) return 3;
        if (key.startsWith("refresh:")) return 4;
        if (key.startsWith("google:")) return 5;
        if (key.startsWith("callback:")) return 6;
        if (key.startsWith("link:")) return 7;
        if (key.startsWith("csrf-client:")) return 8;
        if ("csrf-global".equals(key)) return 9;
        if (key.startsWith("listing-create:")) return 10;
        if (key.startsWith("image-upload:")) return 11;
        if (key.startsWith("search:")) return 12;
        if (key.startsWith("reservation-create:")) return 13;
        if (key.startsWith("report-create:")) return 14;
        if (key.startsWith("ai-scan:")) return 15;
        if (key.startsWith("mission:")) return 16;
        if (key.startsWith("email-verify:")) return 17;
        if (key.startsWith("email-resend:")) return 18;
        if (key.startsWith("mfa-verify:")) return 19;
        if (key.startsWith("mfa-manage:")) return 20;
        throw new IllegalArgumentException("Unknown rate-limit scope.");
    }

    @Scheduled(fixedDelay = 60_000)
    public synchronized void evictIdleBuckets() {
        Instant now = Instant.now();
        for (Bucket[] scope : buckets) {
            for (int index = 0; index < scope.length; index++) {
                Bucket bucket = scope[index];
                if (bucket != null && !bucket.expiresAt.isAfter(now)) scope[index] = null;
            }
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

    private static final class Bucket {
        private final Instant expiresAt;
        private int count;
        private Bucket(Instant expiresAt, int count) { this.expiresAt = expiresAt; this.count = count; }
    }
}
