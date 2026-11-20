# Complete local mail workspace

This mode works without DNS, a domain purchase, Google credentials or an external provider. All email transport stays on the laptop through Mailpit SMTP. It is not internet mail hosting.

## Start

MySQL must be running/provisioned (MYSQL-SETUP.md). Stop any old backend on port 8080, then from this project root:

```powershell
.\start-local-mail.ps1
```

The helper downloads/verifies the pinned official Mailpit release if absent, starts a hidden localhost-only capture server, and starts the backend with `mysql,local-mail`. Enter the MySQL application password at the hidden prompt. In a separate terminal start `frontend` with `npm ci` followed by `npm run dev`.

1. HRMS: http://127.0.0.1:5173/ . Local default administrator is admin@xpertsedge.com / Admin@123; change before deployment.
2. HRMS Mail Admin: create mailboxes such as alice/bob/carol with their own passwords. No demo mailboxes are seeded. Create/edit/delete, password reset, suspension and aliases are effective immediately because local mail auth/delivery resolve the same MySQL records.
3. Webmail: http://127.0.0.1:5173/#/webmail . Use the full mailbox email (`alice@xpertsedge.com`) and its mailbox password, not the HRMS admin password.
4. Compose to another created local mailbox. Add To/Cc recipients, text and optional attachments. **Send locally** uses real localhost SMTP; recipients see the message in their Inbox.

## Available in the same page

- Persistent Inbox, Sent and Drafts; polling every 10 seconds.
- Reply, Reply all (self/duplicate recipients excluded), Forward with independent attachment copies.
- To/Cc lists: up to 20 distinct recipients, comma/semicolon separated. No Bcc compose field.
- Upload/remove attachments in drafts, send/receive and authenticated download. Maximum 5 files, 1 MB total per message. Filenames are reduced to safe basenames. Downloads use attachment disposition, no-store, nosniff and application/octet-stream; HTML attachments are never rendered inline.
- Full server-side search over subject/body/From/To/Cc, across all owned messages, with literal wildcard escaping and 200-result pagination. Tags can filter messages across folders.
- Read/unread and real folder/unread counts, Spam/not-spam, Trash/restore for received messages, drafts and sent messages. Permanent deletion requires Trash and UI confirmation; attachment rows are removed with the message.
- Permanent deletion creates a private tombstone so a retained SMTP capture is not re-imported. Recreated mailbox accounts cannot import captures from before the new account existed. Alias retargeting does not expose old alias mail to its new target.
- Delivery status is explicit: DRAFT, SMTP_ACCEPTED, RECEIVED_LOCAL or FAILED. A transport error keeps the owned draft and failure diagnostic for retry. SMTP acceptance is not a claim of recipient reading or internet delivery.
- Independent HttpOnly mailbox sessions. Cross-mailbox message, attachment, draft and forward-source access is rejected. Reset/suspend revokes mailbox sessions; suspended accounts cannot log in.

## Storage and local ports

- MySQL tables: mailbox/mail_alias and webmail_session/webmail_message/webmail_attachment/webmail_deleted_capture. MySQL V3 adds mail actions/attachments; isolated historical H2 tests use V6. Application runtime still requires MySQL; no H2 file fallback.
- Mailpit raw captures: `.local-mail/messages.db` (SQLite). Binary/logs are under `.local-mail/`; this directory is excluded from source packages. App messages, attachment bytes and drafts persist in MySQL. Back up MySQL plus the stopped capture database/WAL files for recovery.
- SMTP: 127.0.0.1:1025, trusted-local-machine test endpoint without SMTP client authentication or TLS. Mailbox passwords authenticate Webmail only. Native local SMTP tools can address active canonical mailboxes/aliases. Alias messages resolve to the target when imported; local UI sending resolves aliases before SMTP submission.
- Capture admin UI/API: 127.0.0.1:8025; it can see all captures and must never be exposed to untrusted users/networks. The app only exposes owner-scoped messages through its backend.
- Backend: 127.0.0.1:8080; frontend: port 5173. Mailpit is configured without external forwarding/relaying and accepts only @xpertsedge.com recipients. The helper clears inherited Mailpit forwarding settings. `prod` plus `local-mail` is refused by the app.

## Limits

Local development only; no internet sending/receiving, IMAP listener, scheduled delivery, custom folder creation, attachment virus scanning or spam detection. Scheduled remains an explicitly unavailable folder. Spam is a manual folder. Quota fields are local settings, not measured/enforced production quotas. Plain text body <=20,000 characters; subject <=255. Oversized incoming raw attachments are retained in capture storage but omitted from the app with a notice; UI uploads over the limit are rejected. Mail HTTP mutation bodies are capped at 2 MB before JSON parsing.

SMTP and MySQL do not share a distributed transaction. A crash after SMTP acceptance and before SQL commit can make delivery ambiguous; inspect capture/Sent before retrying. Normal repeated submissions of a sent draft ID are rejected. Repeated sends without a saved draft ID are separate messages. There is no production queue, automatic retry loop, delivery/read receipt, antivirus certification or internet delivery guarantee.

## Verify

With MySQL, Mailpit and the local-mail backend running:

```powershell
node check-local-webmail.mjs
node check-local-mail-features.mjs
```

The extended test creates uniquely named disposable accounts, uses actual SMTP with To/Cc/MIME attachments, validates exact received/downloaded bytes, reply-all/forward behavior, owner isolation, search, read state, trash/restore/delete, direct SMTP aliases, retarget safety and recreated-account privacy. It cleans only its own accounts, notices and captures. The separate backend test uses an isolated SMTP stub to verify durable FAILED status and retry behavior without interrupting the user's server. Standard `npm run verify` runs isolated regressions without live user data.

Gmail outbound is optional and disabled in normal local mode. It is not needed here; GMAIL-SMTP.md describes that separate, credential-dependent adapter.


## Personal local-mail modules

- Calendar: create, edit and delete personal events; start/end validation. No invitations, reminders, recurring events or external sync.
- Contacts: create, edit, delete and compose to a saved contact. Each mailbox has its own address book.
- Archive and bulk actions: select loaded messages (maximum 200 per request), read/unread, archive, trash, restore and permanent deletion from Trash. Mixed-account requests fail atomically.
- Filters: read/unread, attachment presence and inclusive UTC date range, alongside existing full-text search and tags.
- Scheduled sending: save the compose message and choose a future computer-local date/time, within one year. The database stores UTC. The worker checks every five seconds, sends locally through Mailpit, and resumes overdue jobs after restart. Cancel a scheduled message to edit it. Failure retains a failed draft. No automatic retry; SMTP acceptance is not guaranteed exactly-once delivery if the process crashes between SMTP acceptance and database commit.
- Settings: signature for new messages, light/dark theme, compact/comfortable rows, display time zone and keyboard shortcuts. These persist in MySQL per mailbox.
- Storage: counts by folder, UTF-8 body bytes and attachment bytes. Excludes indexes, metadata, engine overhead and SMTP capture storage; no enforced quota.
- Shortcuts: `/` focuses search, `C` composes outside text fields, `Esc` closes compose with a dirty-change prompt.
- Remember me: opt-in persistent mail session for 30 days; otherwise a browser-session cookie with one-hour server expiry. Logout, reset, suspension and MFA changes revoke access.
- Webmail 2FA: Settings → authenticator setup; enter current mailbox password, manually add the setup key to a TOTP app (SHA-1 / 6 digits / 30 seconds), then confirm within five minutes. Eight single-use recovery codes are shown once. Save them before signing in again. Login offers a 2FA/recovery-code field. Enabling/disabling revokes all mailbox sessions. Login and security changes are rate limited per account. Reused time-step codes are rejected, including the setup confirmation code; use the next code or a recovery code for your next login.
- TOTP secrets use AES-GCM with a domain-separated key derived from the backend signing secret. Recovery codes are stored as SHA-256 hashes. Back up the signing secret securely with database backups; changing or losing it prevents decryption of existing authenticator secrets. An administrator can reset the mailbox password, but this does not disable MFA. Keep recovery codes safely; there is no administrator MFA-recovery UI.

Run `node check-mail-modules.mjs` for live MySQL/SMTP verification of these modules. Only disposable QA mailboxes and captures are removed by the script.
