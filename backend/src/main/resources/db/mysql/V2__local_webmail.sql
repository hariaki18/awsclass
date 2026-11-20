CREATE TABLE webmail_session (
  id VARCHAR(255) PRIMARY KEY, owner_id BIGINT NOT NULL,
  credential_fingerprint VARCHAR(255) NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_webmail_session_owner FOREIGN KEY (owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
CREATE TABLE webmail_message (
  id VARCHAR(255) PRIMARY KEY, owner_id BIGINT NOT NULL, source_key VARCHAR(255) UNIQUE,
  from_address VARCHAR(255) NOT NULL, to_address VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL, body LONGTEXT, folder VARCHAR(20) NOT NULL,
  tag VARCHAR(20) NOT NULL, seen BOOLEAN NOT NULL, created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_webmail_message_owner FOREIGN KEY (owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
CREATE INDEX ix_webmail_message_owner ON webmail_message(owner_id, created_at);
CREATE INDEX ix_webmail_session_expiry ON webmail_session(expires_at);
