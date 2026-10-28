package pl.lendly.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthUser(UUID id, String email, boolean emailVerified, String passwordHash, String displayName,
                       String avatarUrl, String areaLabel, String role, String status, Instant registeredAt) {
    AuthDtos.PrivateProfile privateProfile(boolean mfaEnabled) {
        return new AuthDtos.PrivateProfile(id, email, emailVerified, displayName, avatarUrl, areaLabel,
                role, status, registeredAt, mfaEnabled, MfaService.required(role), passwordHash != null);
    }
    AuthDtos.PublicProfile publicProfile() {
        return new AuthDtos.PublicProfile(id, displayName, avatarUrl, areaLabel, registeredAt);
    }
}
