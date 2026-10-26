package pl.lendly.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.JwtSecret;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class AuthTokenService {
    private final AuthProperties properties;

    public AuthTokenService(AuthProperties properties) { this.properties = properties; }

    public String issue(UUID userId, UUID sessionId, Instant expiry) {
        byte[] key = key();
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getIssuer()).audience(properties.getAudience()).subject(userId.toString()).claim("sid", sessionId.toString())
                    .issueTime(new Date()).expirationTime(Date.from(expiry)).build();
            SignedJWT jwt = new SignedJWT(new com.nimbusds.jose.JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to issue access token", exception);
        }
    }

    public TokenClaims verify(String value) {
        try {
            SignedJWT jwt = SignedJWT.parse(value);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key()))) {
                throw new IllegalArgumentException();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.getIssuer().equals(claims.getIssuer()) || !claims.getAudience().contains(properties.getAudience())
                    || claims.getSubject() == null || claims.getIssueTime() == null
                    || claims.getIssueTime().toInstant().isAfter(Instant.now()) || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) throw new IllegalArgumentException();
            return new TokenClaims(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getStringClaim("sid")));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required.");
        }
    }

    private byte[] key() {
        try {
            return JwtSecret.decode(properties.getJwtSecret());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Authentication is not configured.");
        }
    }

    public record TokenClaims(UUID userId, UUID sessionId) {}
}
