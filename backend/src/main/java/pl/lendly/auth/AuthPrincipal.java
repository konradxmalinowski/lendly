package pl.lendly.auth;

import java.util.UUID;

public record AuthPrincipal(AuthUser user, UUID sessionId) {}
