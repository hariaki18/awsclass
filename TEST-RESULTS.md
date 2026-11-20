# Local personal-mail modules — 6 November 2026

All eight standard verification stages passed. The final frontend has 15 passing tests and a successful production build. Final backend `mvnw verify`: 28 passed, 2 optional tests skipped, zero failures/errors. The optional legacy-backup and dedicated fresh-MySQL JUnit tests were not enabled; the running MySQL database was separately backed up, migrated to V4 and validated by Hibernate.

`node check-mail-modules.mjs` passed 53 live MySQL/SMTP checks on the final restarted build (57 on an earlier run; the count varies with scheduled-worker polling), including Contacts/Calendar CRUD and ownership, persistent preferences, scheduled SMTP delivery to another Inbox, bulk ownership atomicity, read/date/attachment filters, storage and 2FA enrollment/recovery/session behavior. `node check-local-mail-features.mjs` passed 113 existing mail-feature checks. All fixtures use unique QA mailboxes and are cleaned after each test.

Backend tests include RFC HOTP test vector, encrypted authenticator secret storage, TOTP replay rejection, one-time recovery, remembered cookie lifetime, security-change rate limiting, schedule cancel/due processing, invalid calendar dates, contact validation, cross-owner requests and bulk all-or-nothing changes. Browser checks verified navigation, Contacts create/save, Settings and saved light/dark appearance. Existing user accounts and messages were preserved.

Local-only limitations remain: no internet send/receive or provider provisioning, no external calendar/contact sync, no recurring events or reminders. Scheduler requires backend and Mailpit; no exactly-once crash guarantee. Storage figures exclude database overhead and do not enforce quota. Webmail MFA does not add HRMS admin MFA. See LOCAL-MAIL.md and KNOWN-LIMITATIONS.md.

## Earlier verification reports

# Completed local mail verification — 6 November 2026

All eight standard verification stages passed on the current source: frontend 15 tests/build; backend 23 tests/build (2 optional tests skipped); mailbox CRUD regression 178; basic HR/Organization 52; authentication 31; password rotation 10; production guards 4. The optional legacy-backup and dedicated MySQL-schema JUnit tests were not enabled; actual live local MySQL was separately exercised by the SMTP feature checks.

`node check-local-mail-features.mjs`: 113 real local MySQL/SMTP checks passed on the final restarted build. This covers To/Cc delivery, exact MIME attachment bytes, owner-only downloads, reply-all, forwarding/copying attachments, persistent drafts, literal full-body search beyond 255 characters, read/unread, Sent/Inbox trash restore, permanent-delete anti-reimport tombstones, direct SMTP aliases, retarget safety and deleted/recreated account privacy. Test accounts, aliases, notices and captures were removed using unique test identifiers.

Four isolated backend feature tests additionally cover durable SMTP FAILED status with retained draft/retry, no normal duplicate sent-ID submission, request/file limits, path-safe filenames, download headers, cross-mailbox forwarding/file rejection and search/trash semantics. Gmail adapter configuration tests are offline only; no Google credentials or internet email delivery were used.

Browser verified mailbox login, To/Cc attachment upload -> real SMTP send -> Inbox reading/download link, Reply all prefill and draft save, and Forward preserving attachments. A forwarded draft and its downloadable file survived a real backend process restart. Disposable UI accounts/captures were removed; existing user accounts/data were preserved. Proof screenshot is delivered separately and is not seeded as fake mail.

MySQL was backed up before V3. Flyway schema validation and Hibernate mapping passed. MySQL stores messages/drafts/attachment bytes; local Mailpit also retains raw SMTP captures in SQLite. Local services bind to localhost. No DNS, domain ownership or Google password is required for local mode. Internet mail, scheduled delivery, custom folders, antivirus scanning, provider provisioning and production delivery guarantees are not claimed. See LOCAL-MAIL.md.

## Historical verification notes
# Local SMTP/Webmail verification — 5 November 2026

Current source: all 8 standard verification stages passed (frontend tests 8/build; backend tests/build 16 passed and 2 optional tests skipped; mail regression 178; basic modules 52; authentication 31; password rotation 10; production guards 4). Small final UI message-action/timestamp fixes were followed by another successful frontend production build.

Real MySQL + local Mailpit SMTP integration: `node check-local-webmail.mjs` passed 43 checks, including mailbox create/login, wrong credentials, allowed-origin enforcement, HRMS/mailbox-session isolation, draft persistence, real SMTP-to-recipient Inbox roundtrip, alias delivery, duplicate draft-send rejection, cross-mailbox ID access rejection, read/Spam/Inbox changes, suspend/reactivate session invalidation, password-reset invalidation and logout. A separate direct SMTP check confirmed rejection of outside-domain recipients.

Browser verified light login/layout, mailbox-only login, compose Send locally -> Sent -> Inbox -> message reading. A stored message survived a real backend process restart. All uniquely named integration-test accounts, UI test mailbox and corresponding SMTP captures were removed; user-created accounts were preserved. Proof image is delivered separately, not seeded as application content.

MySQL was backed up before V2 webmail migration; Flyway and Hibernate validation passed against the actual local MySQL server. Raw captures persist in Mailpit SQLite; imported mailbox messages/drafts persist in MySQL. No internet mail, IMAP, attachments or scheduled sending claims are made. See LOCAL-MAIL.md for limits and exact startup.

## Earlier reports (historical scope)
# Webmail UI update — 5 November 2026

Dedicated mail login and Inbox/Compose/Drafts/Sent/Trash preview added. Frontend tests: 8 passed; production build passed. Browser verified admin login to Inbox, compose and save temporary draft, Sent empty state and disabled Send. No messages are sent; drafts are memory-only for the current mounted Mail session and not stored in MySQL. Prior full eight-stage results below predate this UI change; full re-verification is required before repackaging.

# Final source verification — 5 November 2026

All eight stages of `npm run verify` passed on the current source: frontend tests (8) and build, backend tests/build (16 passed, 2 optional tests skipped), mail regression (178), basic HR/user-role regression (52), authentication regression (31), password rotation (10) and production rejection guards (4). See verification-results.json for stage results.

MySQL local connection, Flyway migration, Hibernate validation and login/session through the Vite proxy were also confirmed during local setup. Automated functional regressions use isolated H2 memory databases; the optional real-MySQL schema test and legacy-backup rehearsal were skipped because their test environment variables were not supplied. Full MySQL CRUD regression and existing H2 data transfer remain pending. App runtime requires MySQL and has no H2 file fallback.

PowerShell startup script syntax passed. See MYSQL-SETUP.md for setup and dedicated MySQL tests. Source ZIP excludes credentials, live databases, local JWT keys, logs, dependencies and build output.
# Historical handover test results — 29 October 2026

The historical full `npm run verify` run passed all eight stages. Subsequent V3-to-V4 backup rehearsal also passed, preserving existing data/password-hash fingerprints and prior sequence positions.

| Check | Result |
|---|---|
| Frontend Node automated tests | 8 passed |
| Backend JUnit/schema/migration/health/log-redaction | 17 passed, no failures/errors |
| Local mail/alias/notification isolated HTTP regression | 178 passed |
| Basic HR/Organization/role isolation HTTP regression | 52 passed |
| Authentication/JWT/CSRF/lockout HTTP regression | 31 passed |
| Account password rotation/session revocation | 10 passed |
| Production startup rejection guards | 4 passed |
| Frontend production build | Passed |
| Backend executable JAR build | Passed |

Basic-module checks cover department/employee CRUD/dependency guards; duplicate/invalid dates/emails/roles/passwords; user-account creation/edit/deactivation/reset/delete; employee-account linking; own-profile access; HR/Mail/Employee permission isolation; cross-account notification isolation; session revocation after role/active/password changes; and self-admin deletion/demotion/deactivation protection.

Migration tests cover fresh V1–V4 schema, legacy adoption, missing original-ZIP tables, checksum validation, disabled clean, database constraints and password/row/sequence preservation. V4 creates HR/department tables and adds user roles/active/employee-link fields. Existing pre-role user access is retained as ADMIN for review.

Regression servers use disposable in-memory databases and credentials and stop afterward. No actual administrator password was changed. The optional actual-backup test migrates only a temporary copy; real database backups remain local and are excluded from the source archive.

Browser smoke checks: admin login and role-aware launcher; employee/department screens; department create/edit; employee create with ISO joining date; Organization account list and role/employee-link form. Temporary browser records and their notifications were removed; the real administrator account was unchanged.

Raw logs/XML remain locally in `.verification/` and `backend/target/surefire-reports/`; only this summary and machine-readable stage statuses are included in the clean archive. Results establish tested local functionality, not load capacity, penetration-test certification, other database-engine compatibility or real email delivery.

Dark webmail reference update: frontend tests 8 passed and production build passed. Browser checked dark login, inbox/reading pane, temporary tagged-draft save/preview and tag filtering. Mail logout/expiry now returns to the dedicated mail login. Provider integration and durable drafts remain outside this UI scope.


Gmail adapter update: 3 offline Gmail configuration/TLS/sender/allowlist tests passed; 8 frontend tests and production build passed. Google credentials are not configured and no real Gmail authentication/delivery has been tested. Full standard regression re-verification is required before packaging this adapter update. See GMAIL-SMTP.md.


Webmail compact spacing update: production frontend build passed. Visual checks at 1366x768 and 390x844 verified compact fonts, aligned headers, label/input margin reset, responsive sidebar and visible sticky compose actions. CSS-only update; backend behavior is unchanged. Disposable layout fixture removed.


