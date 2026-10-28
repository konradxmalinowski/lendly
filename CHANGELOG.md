# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Daily AI cost caps: 40 model calls per user and 300 for the whole instance per 24 hours (`LENDLY_AI_USER_DAILY_LIMIT`, `LENDLY_AI_GLOBAL_DAILY_LIMIT`; `0` turns AI off). The instance cap returns `429 AI_DAILY_LIMIT` and the UI explains it.
- Database timeouts for API connections: `LENDLY_DB_STATEMENT_TIMEOUT_MS` (default 15000), `LENDLY_DB_IDLE_TX_TIMEOUT_MS` (default 60000) and an explicit pool `LENDLY_DB_CONNECTION_TIMEOUT_MS` (default 10000). Flyway now uses its own connection so migrations are not cut short.
- Durable rate limiting: counters moved from memory to PostgreSQL (`rate_limit_buckets`, migration V9) and survive restarts. Keys are HMAC digests derived from `LENDLY_RATE_LIMIT_HMAC_KEY`, or from the JWT secret when it is empty. The search limit fails open if the database is down; the others return `503 RATE_LIMIT_UNAVAILABLE`.
- Hourly cleanup of `auth_sessions` and `refresh_sessions` rows that expired more than a day ago.
- Account deletion and personal data export. `POST /api/me/delete` anonymises the account (status `DELETED`, migration V8): open reservations are cancelled, listings are archived and stripped of text, exact location and images, and sign-in data is removed. It asks for the password (or a recent sign-in for Google-only accounts), the MFA code when MFA is on, and the phrase `USUŃ`. `GET /api/me/export` downloads the user's data as JSON. `GET /api/me` gains `hasPassword`.
- Search engine metadata: a Polish title and description per route, canonical links to `https://lendly.com.pl`, Open Graph and Twitter tags, Organization and WebSite JSON-LD on the home page, and Product JSON-LD on listing pages built only from listing data. Private and sign-in pages are `noindex`.
- `robots.txt` and `sitemap.xml`, plus `sitemap-listings.xml` with every active listing (`GET /api/seo/sitemap-listings.xml`).
- Listing pages carry the listing's own title, description, photo and JSON-LD in the HTML that Nginx serves, through SSI and `GET /api/seo/items/{id}/head`, so link previews and crawlers see them without running JavaScript.
- Privacy policy (`/privacy`) and terms of service (`/terms`), linked from the footer and the registration form.
- A 1200x630 link preview image used when a page has no photo of its own.
- A "Nie ma takiej strony" page. Unknown addresses return HTTP 404 instead of redirecting to the home page.
- A "Pomiń mapę" button in the catalog that skips over the map pins and controls.

### Changed

- `GET /api/me/export` is available to suspended accounts that still hold a valid session. Suspending from moderation still revokes all sessions, so in practice this applies to accounts suspended by other means.
- Google buttons on the login page and the profile are hidden until Google sign-in is configured on the server.
- Frontend tooling aligned on `@angular/ssr` 21.2.29; `npm audit` reports no vulnerabilities.
- Google sign-in only signs in to existing accounts. It creates new accounts only with `LENDLY_GOOGLE_SIGNUP_ENABLED=true`; otherwise an unknown address returns to the login page with an explanation. `GET /api/health` gains `features.googleSignUp`, and the registration page shows the Google button only when it is true.
- The mobile menu opens as a panel over the page with a blurred backdrop. Escape or a click on the backdrop closes it, and the page behind it cannot be scrolled or focused.
- The theme switch reads "Ciemny motyw: włączony/wyłączony", and the main navigation marks the current page.
- Nginx caches the unhashed icons for 7 days.
- Map zoom and attribution controls show the theme focus ring.
- The Inter font is bundled with the app instead of loaded from Google Fonts, so visitors' browsers no longer contact Google for it.
- HSTS max-age raised to one year.
- CI builds on `ubuntu-24.04`, and the repository allows only SHA-pinned, GitHub-owned actions.

### Fixed

- The skip link went to the home page on every other page.
- Placeholder text failed contrast in the dark theme.
- Listing form and booking errors were not tied to their fields; map controls had English names; focus did not return after closing the confirm and report forms.

## [0.2.0] - 2026-10-27

### Added

- Email verification. Registration emails a 6-digit code that is valid for 15 minutes, allows 5 attempts, and can be resent after 60 seconds. Unverified users can sign in but cannot make changes until they verify (`403 EMAIL_NOT_VERIFIED`).
- Multi-factor authentication with TOTP and 10 single-use recovery codes. Password and Google sign-in both ask for the code when MFA is on. Login returns `202 { "mfaRequired": true }` and sets a challenge cookie.
- New endpoints: `POST /api/auth/email/verify`, `POST /api/auth/email/resend`, `POST /api/auth/mfa/verify`, `GET /api/me/mfa`, and `POST /api/me/mfa/{setup|enable|recovery-codes|disable}`. `PrivateProfile` gains `mfaEnabled` and `mfaRequired`, and `GET /api/health` gains `features`.
- Mail transports: SMTP when `MAIL_HOST` is set, and a log transport that prints the message in the backend log under the `local` profile with secure cookies off.
- Installable web app (PWA): manifest, icons, and an Angular service worker that caches the app shell only, never `/api`. The app shows an offline notice and a prompt when a new version is ready.
- Open-source files: Apache-2.0 `LICENSE`, `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.editorconfig`, the public environment variable reference in `docs/configuration.md`, and this changelog.
- Environment variables: `LENDLY_REGISTRATION_ENABLED`, `LENDLY_MFA_ENCRYPTION_KEY`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `LENDLY_MAIL_FROM`, and rate limits for email verification and MFA.
- Flyway migrations `V6` (verification, MFA, recovery code, and MFA challenge tables) and `V7` (email verification failure counter).

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
- MFA key rotation without resetting users: set the old key as `LENDLY_MFA_ENCRYPTION_KEY_PREVIOUS`. Stored secrets carry a key id and move to the new key on the next successful TOTP check. Recovery code hashes are now HMACs under a key derived from the MFA key; older codes still work until regenerated.
- Email verification locks after 20 wrong codes over the account's lifetime (`429 VERIFICATION_LOCKED`, cleared by an operator). Migration `V7` adds the counter.
- The MFA challenge cookie is now `__Host-lendly_mfa` with `Path=/` (local name `lendly_mfa`).
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
