# Configuration reference

last_updated: 2026-10-27

The API reads its settings from environment variables. For local development it also imports `backend/.env` (Java properties format) through `spring.config.import: optional:file:.env[.properties]`, resolved against the working directory, so start the API from `backend/`. Real environment variables take precedence over the file. Copy [`backend/.env.example`](../backend/.env.example) to `backend/.env` and never commit it.

This page lists names, formats, and defaults. It contains no values. Defaults come from [`application.yml`](../backend/src/main/resources/application.yml).

## Profiles

| Setting | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Use `local` on a developer machine and `prod` (or `production`) on a server. In the template file this is written as `spring.profiles.active=local`, which only works inside `backend/.env`. The `prod` profile turns on the production checks below. |

The `local` profile allows non-secure cookies on an HTTP loopback origin, the mail log transport (with non-secure cookies), an empty MFA key, and a default image directory. Production-like startup checks fail fast with an error message when a required value is missing or malformed.

## Database

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/lendly` | JDBC URL of a PostgreSQL 17 database. Flyway applies migrations at startup. |
| `DATABASE_USERNAME` | `lendly` | |
| `DATABASE_PASSWORD` | none | Required. |
| `LENDLY_DB_STATEMENT_TIMEOUT_MS` | 15000 | PostgreSQL `statement_timeout` for API connections. A longer statement is cancelled. Flyway uses its own connection and is not subject to it. |
| `LENDLY_DB_IDLE_TX_TIMEOUT_MS` | 60000 | PostgreSQL `idle_in_transaction_session_timeout` for API connections. A session idle inside an open transaction is terminated. |
| `LENDLY_DB_CONNECTION_TIMEOUT_MS` | 10000 | How long a request waits for a pooled connection before failing. The pool size keeps the Hikari default (10). |

## Authentication and sessions

| Variable | Default | Notes |
|---|---|---|
| `LENDLY_JWT_SECRET` | empty | Canonical standard Base64 of at least 32 random bytes (`openssl rand -base64 32`). Required in production. Without it, password sign-in fails closed. The email verification code key is derived from it, so changing it invalidates pending codes and signs everyone out. |
| `LENDLY_JWT_ISSUER` | `lendly-api` | |
| `LENDLY_JWT_AUDIENCE` | `lendly-api` | |
| `LENDLY_RATE_LIMIT_HMAC_KEY` | empty | At least 32 UTF-8 bytes (`openssl rand -hex 32`). Required in production and for password sign-in. Also keys the durable rate-limit buckets; when it is empty, the bucket key is derived from the JWT secret instead. |
| `LENDLY_PUBLIC_ORIGIN` | empty | Exact browser origin without a path. HTTPS in production. Locally, the origin of the Angular dev server. |
| `LENDLY_ALLOWED_ORIGINS` | empty | Comma-separated exact origins allowed to make changing requests. No wildcard. In production it must contain `LENDLY_PUBLIC_ORIGIN`. |
| `LENDLY_SECURE_COOKIES` | `false` | Must be `true` for any HTTPS origin and in production. `false` is accepted only with the `local` profile and an HTTP localhost, 127.0.0.1, or ::1 origin. |
| `LENDLY_TRUSTED_PROXIES` | empty | Comma-separated IPs or CIDRs of reverse proxies whose `X-Forwarded-For` is trusted. Empty means the socket address is used. |
| `LENDLY_REGISTRATION_ENABLED` | `false` | Opens public registration. Registration also needs a mail transport (see Mail), otherwise `POST /api/auth/register` returns `403 REGISTRATION_DISABLED`. |
| `LENDLY_DEMO_REGISTRATION` | `false` | Local and demo use. Opens registration on non-secure cookies. |
| `LENDLY_DEMO_REGISTRATION_ON_HTTPS` | `false` | Lets `LENDLY_DEMO_REGISTRATION` apply to an HTTPS demo instance. Do not use on a public deployment. |

## Multi-factor authentication

| Variable | Default | Notes |
|---|---|---|
| `LENDLY_MFA_ENCRYPTION_KEY_PREVIOUS` | empty | Optional, only during key rotation. The key that was current before; secrets and recovery codes made under it keep working until users sign in with a TOTP code and regenerate recovery codes. Same format as the current key, and different from it and from the JWT secret. Setting it without the current key fails startup. Remove it after the rotation. |
| `LENDLY_MFA_ENCRYPTION_KEY` | empty | Canonical standard Base64 of exactly 32 random bytes (`openssl rand -base64 32`). Encrypts TOTP secrets. Startup fails outside the `local` profile when it is missing, and in any profile when it equals `LENDLY_JWT_SECRET`. In the `local` profile MFA stays unavailable while it is empty. To rotate it, use `LENDLY_MFA_ENCRYPTION_KEY_PREVIOUS`. |

## Mail

| Variable | Default | Notes |
|---|---|---|
| `MAIL_HOST` | empty | SMTP host. When set, the API sends verification codes by SMTP in every profile. |
| `MAIL_PORT` | `587` | |
| `MAIL_USERNAME` | empty | |
| `MAIL_PASSWORD` | empty | |
| `LENDLY_MAIL_FROM` | empty | Sender address. Required when `MAIL_HOST` is set. |

With an empty `MAIL_HOST`, the `local` profile with `LENDLY_SECURE_COOKIES=false` writes each message, including the verification code, to the backend log. Otherwise there is no transport, so registration stays closed and resend returns `503 MAIL_UNAVAILABLE`.

## Google sign-in

Google signs in automatically to an existing local account only when the address is `@gmail.com` or the ID token `hd` claim equals the email domain; other users link from their profile. Google does not create accounts unless `LENDLY_GOOGLE_SIGNUP_ENABLED` is `true`; an unknown Google address is sent back to the login page (`/login?auth=no-account`). Set the first three together, with secure cookies on and `LENDLY_PUBLIC_ORIGIN` set to the same HTTPS origin as the callback. Without them, Google sign-in is unavailable.

| Variable | Default | Notes |
|---|---|---|
| `GOOGLE_OIDC_CLIENT_ID` | empty | OAuth client ID. |
| `GOOGLE_OIDC_CLIENT_SECRET` | empty | OAuth client secret. |
| `LENDLY_GOOGLE_CALLBACK_URL` | empty | Must end in `/api/auth/google/callback` and be registered with Google. |
| `LENDLY_GOOGLE_SIGNUP_ENABLED` | `false` | When `true`, Google sign-in also creates an account for an unknown address. |

## Storage

| Variable | Default | Notes |
|---|---|---|
| `LENDLY_IMAGE_DIR` | empty | Absolute directory for listing images, writable by the service and outside any web root. Required outside the `local` profile. Locally it falls back to `var/images` under the working directory. |

## AI features

AI features are optional. Without a key they are hidden and keyword search still works.

| Variable | Default | Notes |
|---|---|---|
| `AI_PROVIDER` | `gemini` | `gemini` or `openai`. Only the selected provider's key is used. Other values fail startup. |
| `GEMINI_API_KEY` | empty | |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | |
| `GEMINI_BASE_URL` | `https://generativelanguage.googleapis.com` | Changing it fails startup outside the `local` profile. |
| `OPENAI_API_KEY` | empty | |
| `OPENAI_MODEL` | `gpt-6-luna` | |
| `OPENAI_BASE_URL` | `https://api.openai.com` | Changing it fails startup outside the `local` profile. |

## Rate limits

Each limit is a count per window. Exceeding it returns `429 RATE_LIMITED` with `Retry-After`. All counts and windows must be positive. Counters are stored in PostgreSQL (`rate_limit_buckets`), so they survive restarts and are shared by every API instance. Each row is keyed by an HMAC-SHA-256 digest of the limiter key and window, never a raw address, email or user id. The HMAC key is derived with a fixed label from `LENDLY_RATE_LIMIT_HMAC_KEY`, or from `LENDLY_JWT_SECRET` when that is empty; changing either secret resets all counters. With neither set the key is random per process and limits reset on restart. Expired rows are deleted every minute. If the database is unavailable the search limit lets requests through; every other limit rejects with `503 RATE_LIMIT_UNAVAILABLE`.

| Count variable | Default | Window variable | Default (seconds) | Scope |
|---|---:|---|---:|---|
| `LENDLY_RATE_REGISTER` | 5 | `LENDLY_RATE_REGISTER_WINDOW_SECONDS` | 3600 | IP, registration |
| `LENDLY_RATE_LOGIN_IP` | 10 | `LENDLY_RATE_LOGIN_WINDOW_SECONDS` | 900 | IP, sign-in |
| `LENDLY_RATE_LOGIN_EMAIL` | 10 | `LENDLY_RATE_LOGIN_WINDOW_SECONDS` | 900 | IP and email, sign-in |
| `LENDLY_RATE_LOGIN_WIDE_IP` | 60 | `LENDLY_RATE_LOGIN_WINDOW_SECONDS` | 900 | IP, all sign-in attempts |
| `LENDLY_RATE_REFRESH` | 30 | `LENDLY_RATE_REFRESH_WINDOW_SECONDS` | 60 | IP, token refresh |
| `LENDLY_RATE_GOOGLE` | 10 | `LENDLY_RATE_GOOGLE_WINDOW_SECONDS` | 900 | IP, Google sign-in start |
| `LENDLY_RATE_OIDC_CALLBACK` | 20 | `LENDLY_RATE_OIDC_CALLBACK_WINDOW_SECONDS` | 900 | IP, Google callback |
| `LENDLY_RATE_GOOGLE_LINK` | 5 | `LENDLY_RATE_GOOGLE_LINK_WINDOW_SECONDS` | 900 | IP, Google linking |
| `LENDLY_RATE_CSRF_PER_CLIENT` | 20 | `LENDLY_RATE_CSRF_WINDOW_SECONDS` | 60 | IP, CSRF token requests |
| `LENDLY_RATE_CSRF_GLOBAL` | 10000 | `LENDLY_RATE_CSRF_WINDOW_SECONDS` | 60 | Whole instance, CSRF token requests |
| `LENDLY_RATE_EMAIL_VERIFY` | 10 | `LENDLY_RATE_EMAIL_VERIFY_WINDOW_SECONDS` | 900 | User, email code checks |
| `LENDLY_RATE_EMAIL_RESEND` | 5 | `LENDLY_RATE_EMAIL_RESEND_WINDOW_SECONDS` | 3600 | User, code resends (a 60-second cooldown also applies) |
| `LENDLY_RATE_MFA_VERIFY` | 10 | `LENDLY_RATE_MFA_VERIFY_WINDOW_SECONDS` | 900 | IP and user, MFA sign-in step |
| `LENDLY_RATE_MFA_MANAGE` | 10 | `LENDLY_RATE_MFA_MANAGE_WINDOW_SECONDS` | 900 | User, MFA setup and changes |
| `LENDLY_RATE_ACCOUNT_DELETE` | 5 | `LENDLY_RATE_ACCOUNT_DELETE_WINDOW_SECONDS` | 900 | User, account deletion attempts |
| `LENDLY_RATE_DATA_EXPORT` | 5 | `LENDLY_RATE_DATA_EXPORT_WINDOW_SECONDS` | 3600 | User, personal data export |
| `LENDLY_RATE_LISTING_CREATE` | 20 | `LENDLY_RATE_LISTING_CREATE_WINDOW_SECONDS` | 86400 | User |
| `LENDLY_RATE_IMAGE_UPLOAD` | 60 | `LENDLY_RATE_IMAGE_UPLOAD_WINDOW_SECONDS` | 3600 | User |
| `LENDLY_RATE_SEARCH` | 120 | `LENDLY_RATE_SEARCH_WINDOW_SECONDS` | 60 | IP, search and availability |
| `LENDLY_RATE_RESERVATION_CREATE` | 20 | `LENDLY_RATE_RESERVATION_CREATE_WINDOW_SECONDS` | 86400 | User |
| `LENDLY_RATE_REPORT_CREATE` | 10 | `LENDLY_RATE_REPORT_CREATE_WINDOW_SECONDS` | 86400 | User |
| `LENDLY_RATE_AI_SCAN` | 20 | `LENDLY_RATE_AI_SCAN_WINDOW_SECONDS` | 3600 | User |
| `LENDLY_RATE_MISSION` | 30 | `LENDLY_RATE_MISSION_WINDOW_SECONDS` | 3600 | User |

## Deployment script settings

These are read by `deploy/scripts/deploy.sh` from `deploy/hosts.env`, not by the API. Copy `deploy/hosts.env.example`.

| Variable | Notes |
|---|---|
| `WEB_HOST`, `API_HOST` | Hosts for the web server and the API. |
| `SSH_USER` | Account used for key-based SSH. |
| `TRUSTED_PROXY` | Optional IP or CIDR of a TLS-terminating proxy in front of the web host. Empty when clients connect directly. |
