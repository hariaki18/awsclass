# Real mail provider integration — pending selection

The user confirmed that no provider has been selected. No external provider adapter, credentials, domain DNS changes, real account provisioning or delivery tests have been performed.

`MailProvider` and `DisconnectedMailProvider` provide an explicit server-side status boundary. `/api/mail/check` reports `NOT_CONFIGURED` and `provider: none`. The local mailbox/alias module continues to work without claiming to provision real accounts or send mail. This interface is preparation, not a completed real-mail integration.

Before implementing a real adapter, select the provider (for example Zoho, Microsoft 365, Google Workspace, cPanel or Mailcow), confirm the owned domain and obtain provider-specific administrative API access. Configure secrets server-side through the deployment secret manager, not source control or browser forms.

The selected adapter must cover domain verification, mailbox provision/edit/delete, aliases, reset/suspend operations and usage reporting. External IDs, operation status, idempotency/retries and failure reconciliation must be implemented before reporting provisioning success. SMTP or provider message APIs alone do not create real mailbox accounts.

DNS/MX/SPF/DKIM/DMARC setup and a controlled send/receive test to an explicitly authorized recipient are required before claiming real delivery. Until these prerequisites exist, all current mailbox/alias changes remain local records.
