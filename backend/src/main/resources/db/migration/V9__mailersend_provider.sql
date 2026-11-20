ALTER TABLE webmail_message ADD COLUMN provider_message_id VARCHAR(128);
CREATE INDEX ix_webmail_provider_message ON webmail_message(provider_message_id);
