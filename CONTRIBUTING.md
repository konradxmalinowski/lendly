# Contributing to Lendly

Thanks for helping. This page covers how to run Lendly from a clean clone and what a change needs before review. By taking part you agree to the [Code of Conduct](CODE_OF_CONDUCT.md). Report security problems privately as described in [SECURITY.md](SECURITY.md), not in public issues.

Contributions are licensed under the [Apache License 2.0](LICENSE), the same as the project.

## Requirements

- Node.js 24 and npm
- Java 21 and Maven
- PostgreSQL 17 on the default port 5432
- `openssl` for generating keys
- An authenticator app (any TOTP app) if you want to try MFA

## Run it locally

1. Create the database. Use a password you make up for local development:

   ```sh
   createuser --login lendly
   psql -d postgres -c "ALTER ROLE lendly WITH PASSWORD '<local-password>'"
   createdb --owner=lendly lendly
   ```

2. Copy the template and keep the copy private. `backend/.env` is git-ignored.

   ```sh
   cp backend/.env.example backend/.env
   ```

3. Edit `backend/.env`:

   - `DATABASE_URL`: `jdbc:postgresql://127.0.0.1:5432/lendly`. The template points at port 5433, so change it.
   - `DATABASE_PASSWORD`: the password from step 1.
   - Generate the keys:

     ```sh
     openssl rand -base64 32   # LENDLY_JWT_SECRET
     openssl rand -hex 32      # LENDLY_RATE_LIMIT_HMAC_KEY
     openssl rand -base64 32   # LENDLY_MFA_ENCRYPTION_KEY
     ```

     Without `LENDLY_MFA_ENCRYPTION_KEY`, the API starts but MFA is unavailable.
   - `LENDLY_PUBLIC_ORIGIN` and `LENDLY_ALLOWED_ORIGINS`: the origin of the Angular dev server. `npm start` serves `http://localhost:4200` by default, so set both to that. The template says 4201.

   Leave `spring.profiles.active=local`, `LENDLY_SECURE_COOKIES=false`, and `LENDLY_DEMO_REGISTRATION=true` as they are. Every variable is listed in [docs/configuration.md](docs/configuration.md).

4. Start the API from `backend/`. Flyway creates the schema at startup.

   ```sh
   cd backend
   mvn spring-boot:run
   ```

   Check `http://localhost:8080/api/health`. The response has a `features` object showing what this instance offers.

5. Start the web client in another terminal. It proxies `/api` to port 8080.

   ```sh
   cd frontend
   npm ci
   npm start
   ```

   Open `http://localhost:4200`. The service worker runs only in a production build.

### Verification codes without a mail server

With the `local` profile and an empty `MAIL_HOST`, the API does not send email. It writes the message to the backend log instead. Register an account, then look in the terminal that runs the API for the line starting `Local mail transport` and read the 6-digit code from the message. Enter it on the verification page. To use a real SMTP server, set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `LENDLY_MAIL_FROM`.

### Moderator or admin account

There is no public role endpoint. After registering and verifying an account, change its role in your local database with `psql`. The email is stored in lowercase:

```sh
psql -d lendly -c "UPDATE users SET role = 'MODERATOR' WHERE email = '<email>' RETURNING id, role;"
```

Use `ADMIN` instead of `MODERATOR` for the admin tools. Run this only against your own local database. The role applies on the next request. Moderators and admins must enable MFA before they can use the moderation pages, so open the profile page and set up an authenticator app first. That needs `LENDLY_MFA_ENCRYPTION_KEY`.

### Optional features

Google sign-in needs a Google OAuth client and an HTTPS callback, so it is off locally. AI features need `GEMINI_API_KEY` or `OPENAI_API_KEY` for the provider chosen with `AI_PROVIDER`. Without a key they are hidden.

## Build

```sh
cd frontend && npm run build
cd backend && mvn -DskipTests package
```

## Conventions

- User-facing text in the app is Polish. Source code, identifiers, comments, commit messages, and documentation are English.
- Indentation and line endings follow [.editorconfig](.editorconfig): 2 spaces for TypeScript, SCSS, HTML, and YAML, 4 for Java.
- The backend is a modular monolith. Keep each feature in its own package under `backend/src/main/java/pl/lendly/` and use controllers, services, and repositories as the existing packages do.
- Schema changes go in a new Flyway migration. Never edit a migration that has been released.
- Authentication uses HttpOnly cookies, a synchronizer CSRF token, and exact Origin checks. Do not put tokens or credentials in browser storage.
- Treat AI output as untrusted. A model must never supply listing IDs, prices, or availability.
- Public listing responses expose only the displaced public point. The private handover point belongs in owner responses only.
- Do not commit secrets, `.env` files, or host names. Use environment variables.
- The project does not use Docker.

## Tests

The project has no automated test suite yet. Check a change by building both parts and using the feature by hand in the browser and against the API. In the pull request, say what you ran and what you saw. If you want to start a test suite, open an issue first so the approach is agreed before you write it.

## Pull requests

Deployments run only from `main`, after a maintainer merges. Pull requests never trigger a deployment, and workflows from forks wait for maintainer approval.

- Keep a pull request to one change and explain why it is needed.
- Update [docs/api/openapi.yaml](docs/api/openapi.yaml) when an endpoint, field, or error code changes.
- Update [docs/configuration.md](docs/configuration.md) and `backend/.env.example` when you add or change an environment variable.
- Add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md) for any user-visible change, using the Keep a Changelog sections (Added, Changed, Deprecated, Removed, Fixed, Security). The project uses Semantic Versioning, and the maintainers set the version at release.
