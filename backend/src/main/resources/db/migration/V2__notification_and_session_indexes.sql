CREATE INDEX IF NOT EXISTS ix_notification_recipient_time ON app_notification(recipient, created_at, id);
CREATE INDEX IF NOT EXISTS ix_notification_recipient_read ON app_notification(recipient, read);
CREATE INDEX IF NOT EXISTS ix_session_email ON auth_session(email);
CREATE INDEX IF NOT EXISTS ix_session_expiry ON auth_session(expires_at);
CREATE INDEX IF NOT EXISTS ix_alias_target ON mail_alias(target);
