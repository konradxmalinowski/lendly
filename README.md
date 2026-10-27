# Lendly

Lendly is a Polish-language marketplace where people can lend and borrow everyday items.

## Features

- Browse and search listings, including approximate map locations.
- Publish listings and manage reservations for full calendar days. Overlapping reservations are blocked, and cancellation releases the dates.
- Register with email and password and confirm the address with a 6-digit code sent by email. Unverified accounts cannot make changes.
- Sign in with Google OpenID Connect when the instance has a Google OAuth client configured.
- Protect an account with multi-factor authentication (authenticator app and recovery codes). Moderators and administrators must enable it.
- Install the web app on a phone or desktop. The service worker caches the app shell only, never API responses, and the app shows a notice when it is offline.
- Use AI-assisted listing creation and Missions to find existing listings for a task. Gemini and OpenAI can be configured as providers; the database remains the source of listing data.
- Report listings and review reports through moderator and administrator tools.

## Stack

- Angular 21 and TypeScript
- Java 21 and Spring Boot 3.5
- PostgreSQL 17 and Flyway
- MapLibre GL JS with OpenFreeMap

The deployment uses separate Linux machines for the web frontend, API, and database. Docker is not used.

## Requirements

- Node.js 24 and npm
- Java 21 and Maven
- PostgreSQL 17

## Local development

Create a local PostgreSQL role and database. Use a password created for local development:

```sh
createuser --login lendly
psql -d postgres -c "ALTER ROLE lendly WITH PASSWORD '<local-password>'"
createdb --owner=lendly lendly
```

Copy the backend environment template and set its database connection, local JWT secret, HMAC key, and MFA encryption key. Keep the resulting `backend/.env` file private. Adjust `DATABASE_URL` to match your local PostgreSQL port.

```sh
cp backend/.env.example backend/.env
```

Start the API. Flyway applies database migrations at startup.

```sh
cd backend
mvn spring-boot:run
```

The API health endpoint is `http://localhost:8080/api/health`.

In another terminal, install frontend dependencies and start Angular:

```sh
cd frontend
npm ci
npm start
```

Set `LENDLY_PUBLIC_ORIGIN` and `LENDLY_ALLOWED_ORIGINS` in `backend/.env` to the origin printed by the Angular development server. Google sign-in requires a Google OAuth client and a matching callback URL. AI features require a key for the selected provider; they remain unavailable when no key is configured.

Registration needs a mail transport. With the `local` profile and an empty `MAIL_HOST`, the API writes the verification email to its log instead of sending it, so read the 6-digit code from the backend log. On a public deployment set `LENDLY_REGISTRATION_ENABLED=true` together with SMTP settings. The full setup, including how to give a local account a moderator role, is in [CONTRIBUTING.md](CONTRIBUTING.md).

## Build

```sh
cd frontend
npm run build
```

```sh
cd backend
mvn -DskipTests package
```

## Documentation

- [OpenAPI](docs/api/openapi.yaml): the API contract, including error codes.
- [Configuration reference](docs/configuration.md): every environment variable with its default.
- [CONTRIBUTING.md](CONTRIBUTING.md): local setup, conventions, and how to submit a change.
- [SECURITY.md](SECURITY.md): how to report a vulnerability privately.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md): the Contributor Covenant 2.1.
- [CHANGELOG.md](CHANGELOG.md): notable changes by release, following Keep a Changelog and Semantic Versioning.

## License

Lendly is licensed under the [Apache License 2.0](LICENSE).
