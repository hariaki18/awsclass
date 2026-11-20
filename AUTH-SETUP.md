# Admin login and JWT authentication

## Implemented

- BCrypt password verification with a generic incorrect-credentials response.
- Five failed attempts per email within a 15-minute window trigger a 429 lockout. Attempt state persists in the database. The verifier uses a dummy BCrypt hash for unknown users.
- JWT signature, issuer, audience, subject, token ID, issued-at and expiration validation.
- Default 60-minute sessions, configurable using `JWT_MINUTES` (1–1440). No automatic refresh tokens; sign in again on expiry.
- JWTs are delivered in a host-only HttpOnly, SameSite=Strict cookie scoped to `/api`. The browser no longer stores tokens in localStorage, and login JSON does not expose a token.
- Protected requests check a persisted server session and the account's existence. Logout deletes the server session and clears the cookie; copied tokens are rejected afterward.
- Authenticated password change verifies the current password, requires a different new password of at least 12 characters and at most 72 UTF-8 bytes, and revokes all sessions for the account.
- Cookie-authenticated mutations require an allowed Origin. Cross-site mutations are rejected. Bearer tokens remain accepted for explicit API clients, but are not returned in login JSON.
- Exact configured CORS origins, no-store API responses, and no token/key/password logging in the implementation.

## Local development

Existing demo login remains unchanged. A random signing secret is created once at `backend/data/.jwt-secret` instead of using the former public hard-coded secret. Preserve that file for local restarts; never commit or distribute it. Old tokens issued before this change are invalid and users must sign in again once.

Run frontend/backend as usual. Both localhost:5173 and 127.0.0.1:5173 are allowed. Cookies belong to the host used to sign in, so these two hostnames have independent sessions.

To rotate the existing demo administrator password, run `./change-admin-password.ps1` from the project folder. It prompts for existing/new credentials without echoing passwords. This script is provided for the administrator to run; the development password was not changed during implementation.

## Production configuration

Activate `SPRING_PROFILES_ACTIVE=prod` and set these in the deployment environment/secret manager:

- `JWT_SECRET`: a cryptographically random secret of at least 32 bytes; the known old development secret is explicitly rejected.
- `APP_ORIGINS`: exact HTTPS frontend origins separated by commas, with no trailing slash or wildcard. Example: `https://hrms.your-company.example`.
- `ADMIN_EMAIL`: bootstrap administrator email.
- `ADMIN_PASSWORD`: bootstrap password (12+ characters, at most 72 UTF-8 bytes, not the demo password).
- `ADMIN_NAME`: optional display name, defaults to Admin.
- `JWT_MINUTES`: optional session duration, defaults to 60.

The bootstrap account is created only on a new/empty user database. Existing passwords are never overwritten from environment settings. The production profile refuses startup if an existing account still uses the demo password: rotate it using the authenticated password-change flow first, or provision a fresh production database without deleting development data.

Production cookies always include Secure. Serve the frontend and `/api` behind the same HTTPS origin/reverse proxy. The local Vite proxy handles development; production routing/TLS must be configured by the deployment environment. Production database/backup/hosting setup is separate from this change.

Build with `backend/mvnw.cmd package`. The executable is `backend/target/xpertsedge-hrms-backend-1.0.0.jar`; run it with `java -jar ...` and the environment above. Do not upload `backend/data`, local secrets, node_modules, or build caches to GitHub.

## Verification and limits

`check-auth.mjs` tests cookie flags, login, malformed/expired/wrong-issuer/wrong-audience tokens, logout revocation, CSRF restrictions and failed-login lockout. It uses the local generated key to sign negative test cases without printing credentials/tokens. It is a development test and must not run against production.

`check-production-auth.mjs` runs isolated in-memory startup checks for production secret/password/origin guards. `run-password-rotation.mjs` starts an isolated backend on a system-selected free port, runs `check-password-rotation.mjs`, and stops it. It tests password rotation, revocation of two sessions and Secure cookies using disposable credentials.

`check-api.ps1` uses the cookie login and covers the existing application APIs. Browser login, refresh-session restore, and logout were also checked.

Latest verification results are in TEST-RESULTS.md and verification-results.json. The full repeatable command is documented in TESTING.md.

Four server-enforced roles are available: ADMIN, HR_ADMIN, MAIL_ADMIN and EMPLOYEE. See API.md for the access matrix. Self-registration, MFA, email recovery, distributed rate limiting and production TLS/database operations are not included. Multi-instance deployments need a shared atomic limiter and edge/IP rate limiting.

