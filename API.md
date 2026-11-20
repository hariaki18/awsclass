# API reference

Base path `/api`. JSON request/response bodies. Requests through the frontend proxy use the same origin. Login returns an HttpOnly `xe_session` cookie, not a JSON bearer token. Send that cookie on protected requests. Mutations using cookie authentication require an allowed `Origin` header. Never send credentials to an untrusted host.

## Roles

| Role | Access |
|---|---|
| ADMIN | All implemented modules and user management |
| HR_ADMIN | Employee/department management; no Mail Admin/user management |
| MAIL_ADMIN | Local mail administration; no HR/user management |
| EMPLOYEE | Own linked employee profile only |

All authenticated users can read their own account/notifications, change their own password and sign out. Unknown/inactive accounts and revoked sessions return 401; insufficient role returns 403. Existing pre-role accounts retain their previous admin access through V4; review them before deployment.

## Authentication

| Method/path | Body | Result |
|---|---|---|
| POST `/auth/login` | `email`, `password` | `user`, `expiresIn`; sets session cookie |
| GET `/me` | — | `id,name,email,initials,role,active,employeeId` |
| POST `/auth/logout` | — | 204; revokes session and clears cookie |
| POST `/auth/change-password` | `currentPassword`, `password` | 204; revokes all account sessions |

Five failed logins per email in 15 minutes trigger 429. Account passwords require 12+ characters, at most 72 UTF-8 bytes. Deactivated users cannot sign in. Password recovery/email invitations are not implemented.

## HR — ADMIN / HR_ADMIN

| Method/path | Behavior |
|---|---|
| GET/POST `/hr/departments` | List/create departments |
| PUT/DELETE `/hr/departments/{id}` | Update/delete department; employee dependencies block deletion |
| GET/POST `/hr/employees` | List/create employees |
| PUT/DELETE `/hr/employees/{id}` | Update/delete employee; linked user blocks deletion |
| GET `/hr/self` | Any authenticated role: its own linked employee; 404 if unlinked |

Department body:

```json
{"name":"Engineering","description":"Technology team","active":true}
```

Employee body (used for create and update):

```json
{
  "employeeCode":"EMP001",
  "firstName":"Example",
  "lastName":"Employee",
  "email":"employee@example.com",
  "phone":"",
  "jobTitle":"Developer",
  "joiningDate":"2026-10-29",
  "employmentType":"FULL_TIME",
  "status":"ACTIVE",
  "departmentId":1
}
```

Types: `FULL_TIME, PART_TIME, CONTRACT, INTERN`; statuses: `ACTIVE, INACTIVE`. Department ID is optional/null; assigned departments must be active. Code normalizes to uppercase and permits letters/numbers/underscore/dash (max 32). Email normalizes to lowercase and is unique. Names/job title max 100, phone max 32. Joining date must be valid ISO date. Lists return all records; frontend search is local, not server pagination.

## Organization — ADMIN only

| Method/path | Body / behavior |
|---|---|
| GET `/org/users` | Safe account views; no passwords/hashes |
| POST `/org/users` | `name,email,password,role,active,employeeId` |
| PUT `/org/users/{id}` | `name,role,active,employeeId`; login email is immutable |
| POST `/org/users/{id}/reset-password` | `password`; revokes that user's sessions |
| DELETE `/org/users/{id}` | 204; revokes sessions, retains linked employee |

Employee link is optional but must reference an existing employee and cannot be shared by multiple accounts. Accounts and employee records are created separately. Deleting an employee requires unlinking its account first. Administrators cannot delete/deactivate/demote themselves or remove the final active administrator. Use own authenticated change-password endpoint for own password rather than administrative reset. No credential is emailed automatically.

## Local mail — ADMIN / MAIL_ADMIN

| Method/path | Body / behavior |
|---|---|
| GET `/mail/check` | Returns `NOT_CONFIGURED`, provider `none`; no real mail connection |
| GET/POST `/mail/mailboxes` | List/create; `address,displayName,quotaGb,password` |
| PUT `/mail/mailboxes/{id}` | `displayName,quotaGb` |
| DELETE `/mail/mailboxes/{id}` | 204; also deletes dependent aliases |
| POST `/mail/mailboxes/{id}/reset-password` | `password`; local record only |
| POST `/mail/mailboxes/{id}/toggle` | Toggle local active flag |
| GET/POST `/mail/aliases` | List/create; `alias,target` |
| PUT `/mail/aliases/{id}` | `alias,target`; rename/retarget |
| DELETE `/mail/aliases/{id}` | 204; target mailbox remains |

Address/alias/target are local parts, not full email addresses: max 64, letters/numbers/dash/underscore, dots only between non-empty segments. Input normalizes case/outer whitespace. Mailbox and alias names share a namespace. Target must exist. Quota is an integer 1–1000 GB. Mailbox passwords: 8+ characters and max 72 UTF-8 bytes. Storage allocation/password/status are local data only. No real mailbox is provisioned, and no message is sent/received.

## Notifications — own account only

| Method/path | Result |
|---|---|
| GET `/notifications` | Latest 100 own notifications, newest first |
| GET `/notifications/summary` | `unreadCount` across all own notifications |
| POST `/notifications/{id}/read` | Read notification; idempotent |
| POST `/notifications/read-all` | `updated` count |
| DELETE `/notifications/{id}` | 204; dismiss own notification |

Successful local changes create notifications for the acting account; failed writes do not. Other accounts' notification IDs return 404. UI refreshes after actions/open and every 30 seconds, not push delivery.

## Health and errors

Anonymous backend paths: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`. Healthy=200; unhealthy/unready=503. Details hidden. Readiness includes DB connectivity; liveness does not.

API responses include `X-Request-ID` for support correlation. Error JSON generally contains `message` (Spring errors may also include `timestamp,status,error,path`). Statuses: 400 invalid body/dependency/self-lockout; 401 login/session; 403 role/origin; 404 missing/unowned record; 409 database uniqueness/referential conflict; 429 login lockout. Do not depend on exact wording. Runtime secrets/body/header values are not recorded by request logging.

AI endpoints have been removed. Recruitment, attendance, leave and payroll APIs are not implemented.

## Local Webmail API

Requires local-mail profile. Separate `xe_mail_session` HttpOnly SameSite Strict cookie (`/api/webmail`); HRMS and mailbox cookies do not grant each other's privileges. Mutation Origin must be allowed. Mail mutation request bodies are limited to 2 MB. API authentication and ownership remain mandatory for files.

- POST `/api/webmail/login`: `{email,password}` -> `{user}` + cookie. Five failures block that mailbox identity for 15 minutes.
- GET `/api/webmail/me`; POST `/api/webmail/logout` (204).
- GET `/api/webmail/status`: local capture state and optional Gmail authorization.
- GET `/api/webmail/messages?page=0&folder=Inbox&q=term&tag=Green`: owned results, 200 per page; returns messages, total, hasMore, folder counts, unread, connected and optional Gmail sender. Empty folder means all folders. Search covers subject/body/From/To/Cc; `%` and `_` are literal. Files are metadata only (id/name/contentType/size).
- POST `/api/webmail/drafts`; PUT `/api/webmail/drafts/{id}`. Fields: `to`, optional `cc`, `subject`, `body`, optional tag. Recipients are comma/semicolon-separated, at most 20. Body <=20,000, subject <=255. `attachments:[{name,contentType,data:<base64>}]` uploads new files; `attachmentIds:[id]` keeps existing owned draft files (omitting the property preserves existing files). Combined max 5 files/1 MB.
- For forwarding into a new draft: `forwardFromId:<owned message id>` plus optional `forwardAttachmentIds:[source-file-id]`. The server copies validated source bytes into new attachment IDs; another mailbox's source ->404. Clients cannot retain file IDs from another draft/message.
- POST `/api/webmail/send`: same draft fields plus optional saved draft `id`. UI always saves first and sends its ID. Local recipient/alias validation happens before SMTP submission. Success moves to Sent with SMTP_ACCEPTED; duplicate sent IDs ->400. SMTP failure ->502/503, with FAILED persisted on the retained draft. SMTP acceptance is not a read/delivery receipt. Internet recipients require the separate configured/authorized Gmail adapter and cannot be mixed with local recipients.
- PATCH `/api/webmail/messages/{id}`: `seen:boolean`, supported tag, `folder:"Inbox"|"Spam"|"Trash"|"Restore"`. Restore uses the previous original folder; Drafts/Sent can be trashed/restored. Inbox/Spam moves apply to received messages only.
- DELETE `/api/webmail/messages/{id}`: only Trash, permanent deletion, 204. Cascades files and records an owner-scoped capture tombstone to prevent re-import.
- GET `/api/webmail/messages/{id}/attachments/{fileId}`: authenticated owner-only attachment download; octet-stream, attachment Content-Disposition, no-store, nosniff. Cross-owner or wrong message file ID ->404.
- POST `/api/webmail/smtp/check`: optional Gmail-only SMTP authentication check; requires an allowed mailbox and configured credentials. No external email is sent by this check.

See LOCAL-MAIL.md for local security/trust boundaries, storage, startup and tests. No real domain/provider provisioning or internet receiving is implied.

## Local Webmail personal modules

All routes below use the mailbox-only `xe_mail_session` cookie and the existing same-origin mutation policy. HRMS admin cookies cannot authorize these endpoints.

| Method | `/api/webmail` route | Body / behavior |
|---|---|---|
| GET / POST | `/contacts` | List own contacts / create `{name,email,phone,company,notes}` |
| PUT / DELETE | `/contacts/{id}` | Edit own contact / delete |
| GET / POST | `/calendar` | List own events / create `{title,startsAt,endsAt,location,notes}`; ISO UTC timestamps |
| PUT / DELETE | `/calendar/{id}` | Edit own event / delete |
| GET / PUT | `/preferences` | `{signature,theme:light|dark,density:compact|comfortable,timeZone,shortcuts:boolean}` |
| GET | `/storage` | Own body/attachment bytes and folder counts |
| POST | `/schedule` | `{id,scheduledAt}`; existing draft, future ISO instant, local recipients |
| POST | `/schedule/{id}/cancel` | Move own scheduled message back to Drafts |
| POST | `/bulk` | `{ids:[...],action:read|unread|archive|trash|restore|delete}`; 1–200 IDs, atomic |
| GET | `/security` | `{enabled}` |
| POST | `/security/setup` | `{password}`; temporary authenticator key, expires in 300 seconds |
| POST | `/security/confirm` | `{password,code}`; enables 2FA, revokes sessions, returns eight recovery codes once |
| POST | `/security/disable` | `{password,code}`; authenticator or recovery code, revokes sessions |

`POST /login` additionally accepts `rememberMe:boolean` and optional `code` (TOTP or recovery code). A non-remembered cookie has no persistent Max-Age and expires server-side after one hour; remembered sessions last 30 days. Login failures use a generic credential error.

`GET /messages` additionally accepts `read=read|unread`, `after=YYYY-MM-DD`, `before=YYYY-MM-DD`, `hasAttachment=true` and `folder=Archive`. Date filters use UTC inclusive dates. PATCH folder changes also accept `Archive` for sent or received messages. Scheduled messages moved to Trash are canceled and restore as Drafts.

Contacts and calendar field lengths are enforced server-side. Cross-account IDs return 404. Security setup/change is limited to five attempts per mailbox per 15-minute window, resetting after success. Secrets and recovery codes must never be logged.
