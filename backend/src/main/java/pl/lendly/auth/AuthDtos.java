package pl.lendly.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @jakarta.validation.constraints.NotNull String displayName,
            @jakarta.validation.constraints.NotNull String email,
            @jakarta.validation.constraints.NotNull String password
    ) {}
    public record LoginRequest(
            @jakarta.validation.constraints.NotNull String email,
            @jakarta.validation.constraints.NotNull String password
    ) {}
    public record ProfilePatch(String displayName, String areaLabel) {}
    public record CsrfResponse(String csrfToken) {}
    public record GoogleUrlResponse(String authorizationUrl) {}
    public record AuthResponse(PrivateProfile user, String csrfToken) {}
    public record PrivateProfile(UUID id, String email, boolean emailVerified, String displayName,
                                 String avatarUrl, String areaLabel, String role, String status,
                                 Instant registeredAt, boolean mfaEnabled, boolean mfaRequired) {}
    public record MfaRequiredResponse(boolean mfaRequired) {}
    public record CodeRequest(@jakarta.validation.constraints.NotNull @Size(max = 64) String code) {}
    public record RecoveryCodesResponse(java.util.List<String> recoveryCodes) {}
    public record PublicProfile(UUID id, String displayName, String avatarUrl, String areaLabel,
                                Instant registeredAt) {}
}
