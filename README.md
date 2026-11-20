# XpertsEdgeHrms — React + Spring Boot

Workspace with secure login, local Mail Admin, basic HR employee/department management and Organization user/role management. Recruitment remains a coming-soon page. See [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md) for scope.

## Requirements
Java 17+, Node 18+ (Maven is not needed, the project includes the Maven wrapper)

For automated verification use Node 20+ and see [TESTING.md](TESTING.md). Run `npm run verify` from this project root. Database schema upgrades are versioned with Flyway; follow [DATABASE-MIGRATIONS.md](DATABASE-MIGRATIONS.md) for existing databases before the first upgraded startup.

Backend probes and request logging: [HEALTH-LOGGING.md](HEALTH-LOGGING.md). Real mail remains disconnected until provider selection; see [MAIL-PROVIDER-INTEGRATION.md](MAIL-PROVIDER-INTEGRATION.md).

## Run the backend (port 8080)
    cd backend
    .\mvnw.cmd spring-boot:run     (Windows)
    ./mvnw spring-boot:run         (Mac/Linux)
The application now requires MySQL (`MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`); there is no default H2 file fallback. For local startup use `start-mysql.ps1` from the project root. Demo login: **admin@xpertsedge.com / Admin@123**

For MySQL, follow [MYSQL-SETUP.md](MYSQL-SETUP.md). The `mysql` profile includes its own driver and Flyway schema. Local MySQL startup, schema validation and proxied login/session are verified. A new PC/server requires its own MySQL setup. Full MySQL CRUD regression and H2 data transfer remain pending.

## Run the frontend (port 5173)
    cd frontend
    npm install
    npm run dev
Open http://localhost:5173 (Vite proxies `/api` to the backend).

## Company logo
Your XpertsEdge logo is in `frontend/public/` (`logo.png` = X mark, `logo-full.png` = full logo). Replace those files to change it.

## Before going live
- Follow [AUTH-SETUP.md](AUTH-SETUP.md): enable the `prod` profile, configure `JWT_SECRET`, HTTPS `APP_ORIGINS` and bootstrap credentials, and rotate any existing demo password. Browser authentication uses HttpOnly cookies; logout revokes server sessions.
- MySQL is required at runtime. Local connection/startup/login are verified; isolated regressions use H2 memory databases. Full MySQL CRUD regression is pending. PostgreSQL is not configured.
- Mail actions only change local records. A real provider adapter requires provider/domain selection and provisioning/delivery tests.

## API
Full endpoint/role/body/error reference: [API.md](API.md).

Environment reference: [.env.example](.env.example). It contains placeholders only; Spring does not automatically load `.env` files. Inject real values through the deployment environment/secret manager.

Handover index: [HANDOVER.md](HANDOVER.md). Build/test results: [TEST-RESULTS.md](TEST-RESULTS.md). Package a clean source archive with `./package-source.ps1` after verification passes. Never hand over `backend/data`, logs, local secrets or node_modules.


## Local Webmail and SMTP
See [LOCAL-MAIL.md](LOCAL-MAIL.md). Run .\start-local-mail.ps1 from the root to start the local capture server and MySQL backend. Create a mailbox/password in HRMS Mail Admin, then sign in to http://127.0.0.1:5173/#/webmail with its full @xpertsedge.com address. HRMS admin accounts and mailbox accounts are separate. Inbox/Sent/drafts/read state persist in MySQL; raw SMTP capture also persists in .local-mail/messages.db. Local recipients only, no internet delivery, IMAP or attachments. Local mode is refused in production.

## Gmail outbound adapter
See GMAIL-SMTP.md and start-gmail.ps1. Real SMTP connection requires the user's Google App Password entered locally; never put it in source or chat. Allowed local mailboxes are explicit; external From is the connected Gmail account. Gmail Inbox/reply sync is not part of the SMTP adapter; use the Gmail API + OAuth option in GMAIL-OAUTH.md (start-gmail-api.ps1) for per-mailbox send and receive.


Local mail now includes reply/reply-all/forward, To/Cc, attachments (5 files/1 MB), authenticated downloads, persistent drafts, read/unread, all-mail search, tags, trash/restore/permanent delete and explicit SMTP status. Start with start-local-mail.ps1; see LOCAL-MAIL.md. No DNS or Google password is needed for local messages.



Local Webmail also includes personal Calendar/Contacts CRUD, Archive, storage usage, persistent settings (signature/theme/density/time zone), shortcuts, bulk message actions, read/date/attachment filters, scheduled local delivery, optional 30-day remembered sessions and authenticator 2FA with one-time recovery codes. See LOCAL-MAIL.md for setup and limitations. Test live modules with `node check-mail-modules.mjs`.

Default entry: opening the root URL now shows XpertsEdge Webmail directly. HRMS/Mail Admin remains available at /#/home for mailbox management. Mail and HRMS use separate logins.
