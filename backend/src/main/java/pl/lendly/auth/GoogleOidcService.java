package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleOidcService {
    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String JWK_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Duration STATE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_TRANSACTIONS = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthProperties properties;
    private final AuthRepository users;
    private final TransactionTemplate transactions;
    private final Map<String, OidcTransaction> pending = new LinkedHashMap<>();

    public GoogleOidcService(AuthProperties properties, AuthRepository users,
                             TransactionTemplate transactions) {
        this.properties = properties;
        this.users = users;
        this.transactions = transactions;
    }

    public String startSignIn(String csrfContextId) {
        ensureConfigured();
        if (csrfContextId == null) throw csrfError();
        return start(Intent.SIGN_IN, null, null, csrfContextId);
    }

    public String startLink(AuthPrincipal principal) {
        ensureConfigured();
        if (!principal.user().status().equals("ACTIVE")) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account cannot access the service.");
        return start(Intent.LINK, principal.user().id(), principal.sessionId(), null);
    }

    public CallbackResult complete(String state, String code, String providerError, AuthPrincipal principal,
                                   String csrfContextId) {
        OidcTransaction tx = take(state);
        if (tx == null || providerError != null || code == null || !tx.expiresAt.isAfter(Instant.now())) return CallbackResult.failure(false);
        if (tx.intent == Intent.SIGN_IN) {
            if (!tx.csrfContextId.equals(csrfContextId)) return CallbackResult.failure(false);
        } else if (principal == null || !tx.userId.equals(principal.user().id()) || !tx.sessionId.equals(principal.sessionId())
                || !users.activeSession(principal.sessionId(), principal.user().id())) {
            return CallbackResult.failure(true);
        }
        try {
            Identity identity = exchange(tx, state, code);
            if (tx.intent == Intent.LINK) return link(principal, identity);
            AuthUser linked = users.findGoogleIdentity(identity.subject).orElse(null);
            if (linked != null) return linked.status().equals("ACTIVE") ? CallbackResult.signedIn(linked) : CallbackResult.failure(false);
            if (users.emailExists(identity.email)) return CallbackResult.linkRequired();
            try { return CallbackResult.signedIn(createUser(identity)); }
            catch (org.springframework.dao.DuplicateKeyException exception) { return CallbackResult.linkRequired(); }
        } catch (Exception exception) {
            return CallbackResult.failure(tx.intent == Intent.LINK);
        }
    }

    private synchronized String start(Intent intent, UUID userId, UUID sessionId, String csrfContextId) {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(now));
        while (pending.size() >= MAX_TRANSACTIONS) pending.remove(pending.keySet().iterator().next());
        String state = randomValue();
        String nonce = randomValue();
        OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(AUTHORIZATION_URI).clientId(properties.getGoogleClientId())
                .redirectUri(properties.getCallbackUrl()).scope("openid", "email", "profile").state(state)
                .additionalParameters(Map.of("nonce", nonce));
        OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);
        OAuth2AuthorizationRequest authorizationRequest = builder.build();
        String challenge = (String) authorizationRequest.getAdditionalParameters().get("code_challenge");
        if (challenge == null || authorizationRequest.getAttributes().get("code_verifier") == null) {
            throw new IllegalStateException("PKCE setup failed.");
        }
        pending.put(state, new OidcTransaction(intent, userId, sessionId, csrfContextId, nonce,
                authorizationRequest, now.plus(STATE_LIFETIME)));
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
                .queryParam("response_type", "code").queryParam("client_id", properties.getGoogleClientId())
                .queryParam("redirect_uri", properties.getCallbackUrl()).queryParam("scope", "openid email profile")
                .queryParam("state", state).queryParam("nonce", nonce).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").build().encode().toUriString();
    }

    private Identity exchange(OidcTransaction tx, String state, String code) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(properties.getGoogleClientId()).clientSecret(properties.getGoogleClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(properties.getCallbackUrl())
                .scope("openid", "email", "profile").authorizationUri(AUTHORIZATION_URI).tokenUri(TOKEN_URI)
                .issuerUri(ISSUER).jwkSetUri(JWK_URI).clientName("Google").build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
                .redirectUri(properties.getCallbackUrl()).state(state).build();
        var exchange = new OAuth2AuthorizationExchange(tx.authorizationRequest, response);
        var tokenResponse = new RestClientAuthorizationCodeTokenResponseClient()
                .getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(registration, exchange));
        Object tokenValue = tokenResponse.getAdditionalParameters().get("id_token");
        if (!(tokenValue instanceof String idToken)) throw new IllegalArgumentException();
        var jwt = new OidcIdTokenDecoderFactory().createDecoder(registration).decode(idToken);
        String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
        String nonce = jwt.getClaimAsString("nonce");
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaim("email_verified");
        if (!ISSUER.equals(issuer) || !jwt.getAudience().contains(properties.getGoogleClientId())
                || !tx.nonce.equals(nonce) || subject == null || subject.isBlank() || !Boolean.TRUE.equals(emailVerified)
                || email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException();
        return new Identity(subject, email.trim().toLowerCase(Locale.ROOT), jwt.getClaimAsString("name"));
    }

    private CallbackResult link(AuthPrincipal principal, Identity identity) {
        return transactions.execute(status -> {
            AuthUser collision = users.findGoogleIdentity(identity.subject).orElse(null);
            if (collision != null && !collision.id().equals(principal.user().id())) return CallbackResult.failure(true);
            if (collision == null) users.linkGoogle(principal.user().id(), identity.subject, identity.email);
            if (identity.email.equals(principal.user().email())) users.markEmailVerified(principal.user().id());
            return CallbackResult.linked();
        });
    }

    private AuthUser createUser(Identity identity) {
        return transactions.execute(status -> {
            String name = identity.name == null || identity.name.isBlank()
                    ? identity.email.substring(0, identity.email.indexOf('@')) : identity.name.trim();
            if (name.codePointCount(0, name.length()) > 80) name = name.substring(0, name.offsetByCodePoints(0, 80));
            if (name.codePointCount(0, name.length()) < 2 || name.codePoints().anyMatch(Character::isISOControl)) name = "Lendly user";
            return users.createGoogleUser(identity.email, name, identity.subject);
        });
    }

    private synchronized OidcTransaction take(String state) {
        if (state == null || state.length() > 128) return null;
        return pending.remove(state);
    }

    private void ensureConfigured() {
        if (properties.getGoogleClientId().isBlank() || properties.getGoogleClientSecret().isBlank()
                || properties.getCallbackUrl().isBlank() || !properties.isSecureCookies()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_OIDC_UNAVAILABLE", "Google sign-in is not configured.");
        }
    }

    private String randomValue() {
        byte[] data = new byte[32];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private ApiException csrfError() { return new ApiException(HttpStatus.FORBIDDEN, "CSRF_INVALID", "CSRF validation failed."); }

    private record OidcTransaction(Intent intent, UUID userId, UUID sessionId, String csrfContextId, String nonce,
                                   OAuth2AuthorizationRequest authorizationRequest, Instant expiresAt) {}
    private enum Intent { SIGN_IN, LINK }
    private record Identity(String subject, String email, String name) {}
    public record CallbackResult(Kind kind, AuthUser user, boolean linkIntent) {
        static CallbackResult failure(boolean linkIntent) { return new CallbackResult(Kind.FAILURE, null, linkIntent); }
        static CallbackResult linkRequired() { return new CallbackResult(Kind.LINK_REQUIRED, null, false); }
        static CallbackResult linked() { return new CallbackResult(Kind.LINKED, null, true); }
        static CallbackResult signedIn(AuthUser user) { return new CallbackResult(Kind.SIGNED_IN, user, false); }
        public enum Kind { FAILURE, LINK_REQUIRED, LINKED, SIGNED_IN }
    }
}
