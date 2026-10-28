package pl.lendly.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.lendly.auth.AccountDtos.DataExport;
import pl.lendly.common.api.ApiException;
import pl.lendly.storage.ImageStorage;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Self-service account deletion (anonymisation) and personal data export. */
@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String CONFIRM_PHRASE = "USUŃ";
    private final AccountRepository repository;
    private final AuthRepository users;
    private final AuthService auth;
    private final MfaService mfa;
    private final RateLimiter limiter;
    private final ImageStorage storage;
    private final TransactionTemplate transactions;

    public AccountService(AccountRepository repository, AuthRepository users, AuthService auth, MfaService mfa,
                          RateLimiter limiter, ImageStorage storage, TransactionTemplate transactions) {
        this.repository = repository;
        this.users = users;
        this.auth = auth;
        this.mfa = mfa;
        this.limiter = limiter;
        this.storage = storage;
        this.transactions = transactions;
    }

    public void delete(AuthPrincipal principal, AuthDtos.DeleteAccountRequest request) {
        if (request == null || request.confirm() == null
                || !CONFIRM_PHRASE.equals(Normalizer.normalize(request.confirm(), Normalizer.Form.NFC))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                    "The request contains invalid fields.", Map.of("confirm", List.of("must equal " + CONFIRM_PHRASE)));
        }
        UUID userId = principal.user().id();
        limiter.accountDelete(userId);
        AuthUser user = auth.current(principal);
        if (user.passwordHash() != null) {
            if (!auth.passwordMatches(user, request.password())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The password is incorrect.");
            }
        } else {
            mfa.requireRecentLogin(principal.sessionId());
        }
        if (mfa.isEnabled(userId)) {
            if (request.code() == null || request.code().isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MFA_CODE_REQUIRED",
                        "A verification code is required.", Map.of("code", List.of("must not be blank")));
            }
            mfa.confirmCode(userId, request.code());
        }
        List<String> imageKeys = transactions.execute(status -> {
            AuthUser locked = users.lockById(userId).filter(found -> found.status().equals("ACTIVE"))
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                            "This account cannot access the service."));
            if ("ADMIN".equals(locked.role()) && repository.otherActiveAdmins(userId) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The only active administrator cannot be deleted.");
            }
            repository.lockListings(userId);
            repository.cancelOpenReservations(userId);
            repository.clearReservationText(userId);
            List<String> keys = repository.retireListings(userId);
            repository.deleteAuthData(userId);
            repository.clearReportDetails(userId);
            repository.anonymise(userId);
            return keys;
        });
        if (imageKeys != null) imageKeys.forEach(storage::delete);
        log.info("Account deleted, userId={}", userId);
    }

    public DataExport export(AuthPrincipal principal) {
        UUID userId = principal.user().id();
        limiter.dataExport(userId);
        // Data access is a right, so suspended accounts may export; deleted ones may not.
        AuthUser user = users.findById(userId).filter(found -> !found.status().equals("DELETED"))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                        "This account cannot access the service."));
        return transactions.execute(status -> new DataExport(Instant.now(),
                new AccountDtos.Profile(user.id(), user.email(), user.emailVerified(), user.displayName(),
                        user.areaLabel(), user.avatarUrl(), user.role(), user.status(), user.registeredAt()),
                repository.googleLinks(userId), repository.mfa(userId), repository.listings(userId),
                repository.reservations(userId), repository.reports(userId), repository.sessions(userId)));
    }
}
