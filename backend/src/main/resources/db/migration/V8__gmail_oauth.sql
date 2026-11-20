CREATE TABLE gmail_account (
  mailbox_id BIGINT PRIMARY KEY,
  gmail_address VARCHAR(254) NOT NULL,
  refresh_token_cipher VARCHAR(1024) NOT NULL,
  history_id VARCHAR(40),
  last_sync_at TIMESTAMP(6) WITH TIME ZONE NULL,
  connected_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
  last_error VARCHAR(200) DEFAULT '' NOT NULL,
  CONSTRAINT uq_gmail_account_address UNIQUE (gmail_address),
  CONSTRAINT fk_gmail_account_mailbox FOREIGN KEY (mailbox_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
ALTER TABLE webmail_message ADD COLUMN gmail_message_id VARCHAR(64);
ALTER TABLE webmail_message ADD COLUMN gmail_thread_id VARCHAR(64);
CREATE INDEX ix_webmail_message_gmail ON webmail_message(owner_id, gmail_message_id);
