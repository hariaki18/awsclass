# Gmail / Google Workspace outbound SMTP

Adapter is implemented but no real account is connected until the user supplies their Google App Password locally and authentication succeeds. No Gmail credentials are stored in source or this ZIP; no external email has been sent as part of setup.

This adapter uses `smtp.gmail.com:587`, mandatory STARTTLS, certificate hostname verification and an App Password. Google requires 2-Step Verification for App Passwords; organizational policies may hide/disable this option. If App Passwords are unavailable, this adapter cannot authenticate: use an OAuth integration instead (not implemented here). Do not disable Google security controls or enter a normal Google account password.

Official references:
- https://support.google.com/mail/answer/185833
- https://developers.google.com/workspace/gmail/imap/imap-smtp

## Connect locally

1. In your own Google account, enable 2-Step Verification if necessary, then create an App Password for this app: https://myaccount.google.com/apppasswords . Complete these credential steps yourself; do not send the password in chat or screenshots.
2. Ensure a local mailbox exists in HRMS Mail Admin. This is the mailbox allowed to use the shared Gmail sender; HRMS admin and mailbox passwords are unrelated to Google credentials.
3. Stop the old HRMS backend, then from the project root run:

   ```powershell
   .\start-gmail.ps1 -GmailAddress irfanxpertedge@gmail.com
   ```

   Enter the full local mailbox email to authorize, the Google App Password at its hidden prompt, then the MySQL password at its separate prompt. The helper enables `mysql,local-mail,gmail`, starts local SMTP capture if needed, and restores process environment values on exit. It does not save credentials to disk.
4. Sign in to Webmail with the local mailbox email/password. External recipients show the actual connected Gmail sender in From and **Send with Gmail**. Recipients within @xpertsedge.com still use the local SMTP service.
5. Verify Gmail authentication without sending a message: as the logged-in allowed mailbox, POST `/api/webmail/smtp/check` with the normal allowed Origin/cookie. Response `{authenticated:true,senderEmail:...}` means SMTP authentication succeeded, not delivery. Authentication failure ->502 with a generic diagnostic.
6. Only after authentication, compose a harmless test to an address you authorize and check the recipient Inbox/spam folder. SMTP acceptance is not proof of final delivery.

## Behavior and boundaries

- External From is always the configured Gmail username, never a spoofed local @xpertsedge.com identity. A Google Workspace account for an owned/verified domain is needed to send as that domain. No arbitrary From or Reply-To is accepted from clients.
- Only local mailbox identities explicitly listed in GMAIL_ALLOWED_MAILBOXES can use Gmail; other mailboxes get 403. The helper authorizes exactly one mailbox. This is a shared sender, not a separate real Google mailbox provisioned for every HRMS account.
- Gmail replies arrive in Gmail. This change does not add Gmail Inbox/IMAP sync, Google account creation, real-domain MX setup or Google mailbox provisioning.
- Messages submitted through Gmail are saved in the owning local mailbox's Sent folder with the actual Gmail sender. Local messages still use Mailpit and stay local. External SMTP errors leave the draft unsent; inspect Gmail Sent before retrying ambiguous failures.
- No credentials appear in frontend settings. Process environment/secret managers are the configuration boundary. Do not enable JavaMail protocol debug or log authentication values.
- Current local mailbox auth is development-only; the existing prod/local-mail guard remains. This does not make the app production-ready mail hosting.

Environment reference (set only for a Gmail-enabled process):

```text
SPRING_PROFILES_ACTIVE=mysql,local-mail,gmail
GMAIL_SMTP_USERNAME=<your full Google email>
GMAIL_SMTP_APP_PASSWORD=<Google App Password, secret>
GMAIL_ALLOWED_MAILBOXES=<explicit local mailbox email>
```
