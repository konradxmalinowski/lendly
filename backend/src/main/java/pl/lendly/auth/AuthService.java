package pl.lendly.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.lendly.common.api.ApiException;
import pl.lendly.config.AuthProperties;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final SessionService sessions;
    private final CsrfService csrf;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final String dummyHash;
    private final AuthProperties properties;

    public AuthService(AuthRepository repository, SessionService sessions, CsrfService csrf, AuthProperties properties) {
        this.repository = repository;
        this.sessions = sessions;
        this.csrf = csrf;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult register(String displayNameInput, String emailInput, String password) {
        if (!properties.registrationOpen()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED", "Registration is not enabled.");
        }
        String displayName = normalizeDisplayName(displayNameInput);
        String email = normalizeEmail(emailInput);
        validatePassword(password);
        if (repository.findByEmail(email).isPresent()) throw new ApiException(HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        AuthUser user = repository.create(email, passwordEncoder.encode(password), displayName)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account with this email already exists."));
        String sessionCsrf = csrf.issuePendingSessionToken();
        SessionService.IssuedSession issued = sessions.create(user.id(), sessionCsrf);
        return new LoginResult(user, issued, sessionCsrf);
    }

    @Transactional
    public LoginResult login(String emailInput, String password) {
        String email;
        try { email = normalizeEmail(emailInput); }
        catch (RuntimeException exception) { email = "invalid"; }
        AuthUser user = repository.findByEmail(email).orElse(null);
        String hash = user == null || user.passwordHash() == null ? dummyHash : user.passwordHash();
        boolean matches = password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && wellFormedUnicode(password) && passwordEncoder.matches(password, hash);
        if (user == null || user.status().equals("SUSPENDED") || user.passwordHash() == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        String sessionCsrf = csrf.issuePendingSessionToken();
        SessionService.IssuedSession issued = sessions.create(user.id(), sessionCsrf);
        return new LoginResult(user, issued, sessionCsrf);
    }

    public AuthUser current(AuthPrincipal principal) {
        return repository.findById(principal.user().id()).filter(user -> user.status().equals("ACTIVE"))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account cannot access the service."));
    }

    public AuthUser updateProfile(AuthPrincipal principal, AuthDtos.ProfilePatch patch,
                                  boolean displayNamePresent, boolean areaLabelPresent) {
        current(principal);
        if (!displayNamePresent && !areaLabelPresent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_PATCH", "At least one editable field is required.");
        }
        String displayName = displayNamePresent ? normalizeDisplayName(patch.displayName()) : null;
        String areaLabel = areaLabelPresent && patch.areaLabel() != null ? normalizeAreaLabel(patch.areaLabel()) : null;
        return repository.updateProfile(principal.user().id(), displayNamePresent, displayName, areaLabelPresent, areaLabel);
    }

    public AuthUser publicProfile(String idValue) {
        UUID id;
        try { id = UUID.fromString(idValue); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "The user ID is invalid."); }
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The user was not found."));
    }

    private String normalizeEmail(String value) {
        if (value == null || !wellFormedUnicode(value)) throw validation();
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() < 3 || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || email.chars().anyMatch(Character::isWhitespace)
                || email.indexOf('@') <= 0 || email.indexOf('@') != email.lastIndexOf('@')
                || email.endsWith("@") || email.startsWith("@")) throw validation();
        return email;
    }

    private String normalizeDisplayName(String value) {
        if (value == null || !wellFormedUnicode(value)) throw validation();
        String trimmed = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count < 2 || count > 80 || trimmed.codePoints().anyMatch(Character::isISOControl)) throw validation();
        return trimmed;
    }

    private String normalizeAreaLabel(String value) {
        if (!wellFormedUnicode(value)) throw validation();
        String trimmed = value.trim();
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count < 2 || count > 120 || trimmed.codePoints().anyMatch(Character::isISOControl)) throw validation();
        return trimmed;
    }

    private void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72 || !wellFormedUnicode(password)) throw validation();
    }

    private ApiException validation() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.");
    }

    private boolean wellFormedUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(current)) return false;
        }
        return true;
    }

    public record LoginResult(AuthUser user, SessionService.IssuedSession session, String csrfToken) {}
}
