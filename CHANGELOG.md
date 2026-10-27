# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-10-27

### Added

- Email verification. Registration emails a 6-digit code that is valid for 15 minutes, allows 5 attempts, and can be resent after 60 seconds. Unverified users can sign in but cannot make changes until they verify (`403 EMAIL_NOT_VERIFIED`).
- Multi-factor authentication with TOTP and 10 single-use recovery codes. Password and Google sign-in both ask for the code when MFA is on. Login returns `202 { "mfaRequired": true }` and sets a challenge cookie.
- New endpoints: `POST /api/auth/email/verify`, `POST /api/auth/email/resend`, `POST /api/auth/mfa/verify`, `GET /api/me/mfa`, and `POST /api/me/mfa/{setup|enable|recovery-codes|disable}`. `PrivateProfile` gains `mfaEnabled` and `mfaRequired`, and `GET /api/health` gains `features`.
- Mail transports: SMTP when `MAIL_HOST` is set, and a log transport that prints the message in the backend log under the `local` profile with secure cookies off.
- Installable web app (PWA): manifest, icons, and an Angular service worker that caches the app shell only, never `/api`. The app shows an offline notice and a prompt when a new version is ready.
- Open-source files: Apache-2.0 `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.editorconfig`, the public environment variable reference in `docs/configuration.md`, and this changelog.
- Environment variables: `LENDLY_REGISTRATION_ENABLED`, `LENDLY_MFA_ENCRYPTION_KEY`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `LENDLY_MAIL_FROM`, and rate limits for email verification and MFA.
- Flyway migration `V6` adds the verification, MFA, recovery code, and MFA challenge tables.

### Changed

- Google sign-in links to an existing local account automatically only when that account's email is verified. A verified Google owner takes over an unverified local account and its password is removed.
- Public registration needs a working mail transport. It stays closed when none is configured, including on the demo.
- Existing accounts are marked as verified by the migration.
- The reference Nginx configuration reads an optional `__TRUSTED_PROXY__` address, filled in by `deploy.sh` from `TRUSTED_PROXY` in `deploy/hosts.env`. It also revalidates the service worker files and the manifest.
- Version is now 0.2.0 in `frontend/package.json`, `backend/pom.xml`, and the jar name used by `deploy.sh`.

### Security

- MFA is required for moderators and administrators. Until they enable it, moderation endpoints return `403 MFA_SETUP_REQUIRED`.
- TOTP secrets are encrypted at rest with AES-256-GCM. The API refuses to start outside the `local` profile without `LENDLY_MFA_ENCRYPTION_KEY`.
- MFA changes (setup, enable, recovery codes, disable) need an auth session from the last 15 minutes (`403 REAUTH_REQUIRED`), so a stolen older session cannot enrol its own factor.
- Wrong MFA codes lock the user durably: the 5th consecutive miss locks for 15 minutes, each further 5 doubles it up to 24 hours, and a correct code resets it (`429 MFA_LOCKED` with `Retry-After`).
- Google auto-link and takeover apply only to `@gmail.com` addresses or when the ID token `hd` claim equals the email domain; other domains link from the profile. A takeover also deletes the account's earlier Google identities and MFA data. Explicit Google linking needs a verified email (`403 EMAIL_NOT_VERIFIED`).
- The API refuses to start if `LENDLY_MFA_ENCRYPTION_KEY` equals the JWT secret.
- Verification codes are stored only as an HMAC, and failed attempts are counted in the same transaction as the check.
- Someone who registers an address they do not own can no longer keep it: the verified Google owner takes over the unverified account.

## [0.1.0] - 2026-10-26

Initial MVP.

### Added

- Spring Boot API and PostgreSQL schema managed by Flyway.
- Angular client with Polish UI text.
- Listings with search, categories, images, and approximate map locations. The private handover point is shown only to the owner.
- Reservations for full calendar days with overlap blocking, owner availability blocks, and the accept, reject, cancel, handover, and return flow.
- Cookie-based authentication with CSRF protection, email and password sign-in, and Google OpenID Connect sign-in.
- Reporting of listings and users, and moderator and administrator tools.
- AI-assisted listing creation and Missions, with Gemini or OpenAI as the configured provider.
- OpenAPI description, deployment scripts, Nginx and systemd configuration.
