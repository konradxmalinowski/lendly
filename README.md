# Lendly

Lendly is a Polish-language marketplace where people can lend and borrow everyday items.

## Features

- Browse and search listings, including approximate map locations.
- Publish listings and manage reservations for full calendar days. Overlapping reservations are blocked, and cancellation releases the dates.
- Sign in with Google OpenID Connect or use email and password in local and demo environments.
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

Copy the backend environment template and set its database connection, local JWT secret, and HMAC key. Keep the resulting `backend/.env` file private. Adjust `DATABASE_URL` to match your local PostgreSQL port.

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

Email and password registration is limited to local and demo use. Do not enable it on a public production deployment until email verification is implemented.

## Build

```sh
cd frontend
npm run build
```

```sh
cd backend
mvn -DskipTests package
```

The API contract is documented in [OpenAPI](docs/api/openapi.yaml).
