# MailerSend setup — xpertsedgetech.com

This application sends Internet email through MailerSend. Mailpit and local SMTP are disabled by default and are only available with the `local-mail` development profile.

## DNS records

The DNS administrator must add the exact values MailerSend displays for `xpertsedgetech.com`. Current intended setup:

| Type | Host | Value | Priority |
| --- | --- | --- | --- |
| TXT | `@` | `v=spf1 include:_spf.mailersend.net ~all` | — |
| CNAME | `ms1._domainkey` | `ms1._domainkey.mailersend.net` | — |
| CNAME | `ms2._domainkey` | `ms2._domainkey.mailersend.net` | — |
| CNAME | `mta` | `mailersend.net` | — |
| MX | `inbound` | `inbound.mailersend.net` | 10 |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@xpertsedgetech.com` | — |

Do not create a second SPF TXT record. Merge MailerSend with any existing SPF value. Start DMARC with `p=none`; tighten it only after observing valid mail.

## Inbound routing

1. In MailerSend, enable **Inbound domain forwarding** for `inbound.xpertsedgetech.com`.
2. Create an inbound route that posts to `https://api.xpertsedgetech.com/api/mail/inbound`.
3. Copy the route signing secret into the deployment secret `MAILERSEND_INBOUND_SIGNING_SECRET`.
4. Send MailerSend's signed test request. The endpoint returns HTTP 200 only for a valid signature.

## Delivery and bounce events

Create a separate MailerSend **Webhook** for `https://api.xpertsedgetech.com/api/mail/events`. Subscribe to `activity.sent`, `activity.delivered`, `activity.soft_bounced`, `activity.hard_bounced`, `activity.spam_complaint`, and `activity.suppressed`. Store that webhook's signing secret as `MAILERSEND_EVENTS_SIGNING_SECRET`. This updates the Sent item to Delivered or Failed without exposing email content in logs.

Outgoing mail uses `user@xpertsedgetech.com` as From and `user@inbound.xpertsedgetech.com` as Reply-To. This keeps the existing main-domain MX records untouched while routing replies into the matching HRMS mailbox.

## Deployment secrets and configuration

Set these in the deployment environment or secret manager, never in source control:

```text
SPRING_PROFILES_ACTIVE=prod,mysql
APP_ORIGINS=https://hrms.xpertsedgetech.com
HRMS_MAIL_DOMAIN=xpertsedgetech.com
MAILERSEND_API_TOKEN=<MailerSend API token with email-send access>
MAILERSEND_INBOUND_DOMAIN=inbound.xpertsedgetech.com
MAILERSEND_INBOUND_SIGNING_SECRET=<Inbound route signing secret>
MAILERSEND_EVENTS_SIGNING_SECRET=<Delivery-events webhook signing secret>
MYSQL_URL=<production JDBC URL>
MYSQL_USERNAME=<database user>
MYSQL_PASSWORD=<database password>
JWT_SECRET=<32+ byte random secret>
ADMIN_EMAIL=admin@xpertsedgetech.com
ADMIN_PASSWORD=<unique initial password>
```

The public backend must be available over HTTPS at `https://api.xpertsedgetech.com`. Configure reverse proxy forwarding and a health check at `/actuator/health`.

## Local verification

For a temporary local run, use `./start-mailersend.ps1`. It prompts for the three sensitive values and stores them only in the PowerShell process. A public HTTPS tunnel is still required for inbound webhook testing; do not expose a local endpoint without HTTPS and access controls.

## Go-live check

1. MailerSend account and `xpertsedgetech.com` show **Verified**.
2. Configure the API token and inbound signing secret in deployment.
3. Deploy backend; verify `/actuator/health`.
4. Send from an HRMS mailbox to Gmail, Outlook, and Yahoo.
5. Reply to the received email and confirm it appears in that mailbox Inbox.
6. Inspect headers at the recipient: SPF, DKIM, and DMARC should pass.
