# Deployment handover

Start with README.md, API.md, .env.example, TEST-RESULTS.md and KNOWN-LIMITATIONS.md.

## Included scope

- Login/JWT cookie sessions, logout revocation, account password operations and role enforcement.
- Local mailbox/alias CRUD and notifications.
- Basic HR employee/department CRUD and read-only own linked employee profile.
- Organization account create/edit/delete, role assignment, employee linking and active status.
- Health/readiness probes, application request logging, MySQL/Flyway migrations and automated tests (isolated tests use H2 memory databases).

Recruitment and advanced attendance/leave/payroll/company workflows are outside this basic phase. Real mail is disconnected; AI is removed. There is no automatic employee-to-login creation or invitation email.

## Repository layout

- `frontend/`: React/Vite source, assets, dependency lockfile and client tests.
- `backend/`: Spring Boot source, Maven wrapper, versioned SQL and JUnit tests.
- Root verification scripts: isolated functional/security regressions and packaging.
- `verification-results.json`: latest stage results. `SOURCE-MANIFEST.json` in the archive lists included source file SHA-256 hashes.

The source archive excludes local databases, JWT key files, credentials, logs, .backups, node_modules, target, dist, editor state and historical scratch reports. Test result summaries are included, not potentially environment-specific raw logs.

## Reproduce

1. Install Java JDK 17+ and Node.js 20+.
2. From `frontend`, run `npm ci`.
3. Stop a running backend before rebuilding its JAR on Windows.
4. From the project root, run `npm run verify`. Exit code must be 0 and `verification-results.json` must show `passed: true` across every stage.
5. Optional actual-legacy rehearsal: set `MIGRATION_BACKUP` to a verified stopped H2 backup. Without it, that one optional test is skipped; synthetic migration checks still run.
6. Build artifacts are `frontend/dist/` and `backend/target/xpertsedge-hrms-backend-1.0.0.jar`. They are generated from source, not bundled with the clean source archive.

## Configure and deploy

Set environment variables from `.env.example`; replace every placeholder. It is a reference, not an auto-loaded dotenv config. Use `SPRING_PROFILES_ACTIVE=prod`, a random signing secret, exact HTTPS origins and unique bootstrap credentials. Do not place secrets in frontend `VITE_` variables.

The initial admin is bootstrapped only for an empty user database. Existing pre-V4 accounts retain ADMIN access; review their roles and rotate demo credentials before production. Production refuses demo passwords/known signing secrets and requires HTTPS origins.

MySQL is required for application runtime. Follow MYSQL-SETUP.md to provision an empty database/user and configure MYSQL_URL, MYSQL_USERNAME and MYSQL_PASSWORD. Local connection, schema validation and proxied login/session were verified. H2-to-MySQL data transfer is not automatic; retain the old database backup. DATABASE-MIGRATIONS.md describes historical H2 upgrades. Keep a verified pre-upgrade backup and previous application artifact for recovery.

Serve frontend and `/api` behind the same HTTPS origin. Reverse proxy/TLS, database/storage provisioning, startup service, firewall, CI/CD, backups/restore, centralized monitoring and rollout/rollback are DevOps work. Probe the backend using HEALTH-LOGGING.md.

## Acceptance

Check login/refresh/logout, role isolation, department/employee CRUD, dependency guards, account activation/reset/revocation, local mail CRUD, own notifications, health/readiness, and log correlation. Real email delivery cannot be accepted until an owned domain/provider and an authorized delivery test exist.

## Local SMTP development mode
See LOCAL-MAIL.md. The local-mail profile adds independent mailbox login and persistent webmail messages/drafts via local SMTP capture; it cannot be used with prod. Mailpit is an official third-party dependency downloaded by start-local-mail.ps1; its binary/data are excluded from this archive. SMTP capture is localhost-only and has no external relay. MySQL V2 adds webmail tables; historical H2 migration tests now include V5. HRMS and mailbox credentials remain separate.


Local mail feature completion (6 November 2026): reply/reply-all/forward, To/Cc, attachment upload/download, persistent drafts, server-side search, read counts, trash/restore/permanent deletion and SMTP status are implemented for local accounts. Limits and startup are documented in LOCAL-MAIL.md. MySQL V3 / H2-test V6 contain the additive schema changes. Real provider/domain provisioning remains out of scope until a provider account is selected/configured.

