# Company Gmail API + OAuth (send and receive)

This build uses **one company Gmail or Google Workspace account**, not one Gmail connection per Webmail user. A designated Webmail administrator connects the company account once. After that:

- every Webmail user can compose and send to any external address directly from Webmail;
- outgoing mail is sent by the connected company Gmail account, so recipients see that Gmail address as the sender;
- replies to messages sent by a user are imported back into that user's Webmail Inbox by Gmail thread;
- new, unthreaded messages from the connected Gmail Inbox or Spam folder are imported into the designated administrator's Inbox.

The Google approval is required once by the company Gmail owner because Google must issue a refresh token. Ordinary Webmail users do **not** connect their personal Gmail accounts or approve Google access.

Mail to `@xpertsedge.com` addresses stays local. Without an owned `xpertsedge.com` domain and DNS/MX records, it is not a real public receiving address.

## One-time Google Cloud setup

1. Enable **Gmail API** in the Google Cloud project.
2. Configure the OAuth consent screen. For a personal Gmail account choose **External** and add the company Gmail address under **Test users**. For production Google Workspace, use an organisation-controlled account and complete Google's required verification if applicable.
3. Create an OAuth client of type **Web application**.
4. In **Authorised JavaScript origins**, enter exactly `http://localhost:5173`.
5. In **Authorised redirect URIs** (a separate section), enter exactly `http://localhost:8081/api/webmail/gmail/callback`. This is the default port used by `start-gmail-api.ps1`. If you start it with another port, register the same port in the redirect URI.
6. Never commit, email, or paste the OAuth client secret. If it was exposed, reset it in Google Cloud before continuing. `.gitignore` excludes client-secret files.

## Run locally

From the project root in PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\start-gmail-api.ps1
```

The script prompts for the Google client ID, client secret, MySQL password, and **company Webmail administrator email**. Enter an existing local mailbox address, for example `arun@xpertsedge.com`. Start the matching frontend in a second PowerShell window:

```powershell
.\start-frontend-gmail.ps1
```

Open `http://localhost:5173`, sign in as that designated Webmail administrator, open **Webmail → Settings**, and select **Connect company Gmail**. Complete the Google approval one time. All local Webmail users can then use **Compose → Send with Gmail** without another Google connection.

## Environment variables

Enable the `gmail-api` Spring profile and set:

```text
GOOGLE_CLIENT_ID=<web client id>.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<stored in a secret manager>
GOOGLE_REDIRECT_URI=http://localhost:8081/api/webmail/gmail/callback
GMAIL_COMPANY_ADMIN_MAILBOX=arun@xpertsedge.com
GMAIL_TOKEN_KEY=<base64 encoding of 32 random bytes>
```

`GMAIL_COMPANY_ADMIN_MAILBOX` is the only local Webmail user allowed to connect, disconnect, and manually sync the company Gmail account. Store these values in deployment secrets, never source control.

## Security and behaviour

- OAuth uses PKCE S256, one-time state (10-minute lifetime), `access_type=offline`, and `prompt=consent`.
- Google refresh tokens are AES-256-GCM encrypted in MySQL. The encryption key is `GMAIL_TOKEN_KEY`, or it is derived from the application signing secret when the key is blank. Access tokens stay in memory.
- The app does not receive or store the Google account password.
- `gmail.send` and `gmail.readonly` allow send and import. Read/unread, archive, and trash actions made inside Webmail do not change Gmail.
- Disconnect revokes the Google token where possible and stops all external send/import. Messages already imported stay in MySQL.
- The scheduler polls the one company Gmail account every 45 seconds by default. Reply routing relies on Gmail thread IDs. A message with no matching Webmail thread goes to the company administrator's Inbox.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/webmail/gmail/status` | Company connection state and whether current user can manage it |
| GET | `/api/webmail/gmail/connect` | Google consent URL; company administrator only |
| GET | `/api/webmail/gmail/callback` | Google redirect target |
| POST | `/api/webmail/gmail/sync` | Manual sync; company administrator only |
| POST | `/api/webmail/gmail/disconnect` | Remove company connection; company administrator only |

## Verification

- `cd backend; .\mvnw.cmd test` runs the backend tests.
- `npm --prefix frontend run build` builds the frontend.
- Manual live test after connection: send from Webmail to a second Gmail address; reply to that message; wait about one minute and verify the reply appears in the original sender's Webmail Inbox.

## Limits

- This uses the connected Gmail address as the public sender. It does **not** create public `@xpertsedge.com` email accounts.
- Public mail for `@xpertsedge.com` needs a purchased domain, DNS ownership, MX/SPF/DKIM/DMARC records, and a provider such as Google Workspace, Zoho, or Microsoft 365.
- Personal Gmail has sending quotas (commonly about 500 recipients/day); Google Workspace quotas vary by plan. Attachments are limited by this app to five files and 1 MB total.
- Google restricts the `gmail.readonly` scope. External production use may require Google verification; testing accounts may need to reconnect when Google expires test tokens.
