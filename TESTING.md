# Automated verification

Prerequisites: Java JDK 17+, Node.js 20+, and frontend dependencies installed with `npm ci` in `frontend`. Maven dependencies download through the included wrapper. No external AI/mail accounts are required.

Stop the normal backend before rebuilding on Windows: a running Java process can lock the executable JAR.

From the project root:

```powershell
npm run verify
```

This exits non-zero on the first failure and runs:

1. Frontend tests (`npm test`) using Node's built-in test runner.
2. Frontend production build (`npm run build`).
3. Backend JUnit migration/Spring-schema/health/log-redaction tests and executable JAR build (`mvnw.cmd -q clean verify`).
4. 177+ isolated local mailbox/alias/notification HTTP checks (cleanup count depends on which concurrent create wins).
5. 31 isolated authentication/security HTTP checks, including a valid test-signed token before the negative claim cases.
6. 52 isolated basic employee/department/user-role management and permission checks.
7. 10 isolated password-rotation/session-revocation checks.
8. Four production startup guard checks.

`verification-results.json` contains step status, exit codes and durations. Full logs are in `.verification/` (ignored by Git). Backend JUnit XML/text reports are in `backend/target/surefire-reports/`.

Regression instances use in-memory databases, disposable credentials and automatically assigned ports. They are stopped afterward. The normal database/admin password is not changed by these tests. The optional backup rehearsal in DATABASE-MIGRATIONS.md modifies only a temporary copy.

Artifacts:

- Frontend: `frontend/dist/`
- Backend: `backend/target/xpertsedge-hrms-backend-1.0.0.jar`

Run the backend with its working directory set to `backend` to keep its relative H2 data/key paths consistent. Production secret/HTTPS instructions remain in AUTH-SETUP.md. For an existing pre-Flyway database, follow DATABASE-MIGRATIONS.md before starting the new artifact.

These tests cover the implemented local modules, authentication and H2 schema. They do not certify real mail delivery, other database engines, full HRMS modules, public TLS deployment, load capacity or a penetration test.

Local feature verification: start local-mail + MySQL + Mailpit, then run node check-local-mail-features.mjs (113 checks). It creates and removes only uniquely named disposable fixtures. Standard backend tests include deterministic SMTP failure/retry and upload/owner isolation checks; browser/actual SMTP tests complement these. See LOCAL-MAIL.md.



`node check-mail-modules.mjs` requires running local MySQL/Mailpit/backend. It creates two unique QA mailboxes and checks personal-module CRUD/isolation, preferences, real scheduled SMTP delivery, bulk atomic ownership, filters, storage, authenticator enrollment, recovery-code single-use, session revocation and remembered/session cookies. It cleans its own fixtures. `WebmailFeaturesTest` also covers these flows in an isolated database, RFC HOTP test vector, encrypted secret storage, validation failures, schedule cancel and due processing.
