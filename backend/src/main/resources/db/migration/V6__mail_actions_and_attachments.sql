ALTER TABLE webmail_message ALTER COLUMN to_address VARCHAR(2000) NOT NULL;
ALTER TABLE webmail_message ADD COLUMN cc_address VARCHAR(2000) DEFAULT '' NOT NULL;
ALTER TABLE webmail_message ADD COLUMN reply_to_address VARCHAR(2000) DEFAULT '' NOT NULL;
ALTER TABLE webmail_message ADD COLUMN original_folder VARCHAR(20) DEFAULT 'Inbox' NOT NULL;
ALTER TABLE webmail_message ADD COLUMN delivery_status VARCHAR(30) DEFAULT 'DRAFT' NOT NULL;
ALTER TABLE webmail_message ADD COLUMN delivery_error VARCHAR(500) DEFAULT '' NOT NULL;
UPDATE webmail_message SET original_folder=CASE WHEN folder='Trash' THEN 'Inbox' ELSE folder END,delivery_status=CASE WHEN folder='Sent' THEN 'SMTP_ACCEPTED' WHEN folder='Drafts' THEN 'DRAFT' ELSE 'RECEIVED_LOCAL' END;
CREATE TABLE webmail_attachment (
 id VARCHAR(255) PRIMARY KEY, message_id VARCHAR(255) NOT NULL,
 filename VARCHAR(255) NOT NULL, content_type VARCHAR(120) NOT NULL,
 byte_size BIGINT NOT NULL, data BLOB NOT NULL,
 CONSTRAINT fk_webmail_attachment_message FOREIGN KEY(message_id) REFERENCES webmail_message(id) ON DELETE CASCADE,
 CONSTRAINT ck_webmail_attachment_size CHECK(byte_size BETWEEN 0 AND 1048576)
);
CREATE INDEX ix_webmail_attachment_message ON webmail_attachment(message_id);
CREATE TABLE webmail_deleted_capture (
 id VARCHAR(255) PRIMARY KEY, owner_id BIGINT NOT NULL,
 CONSTRAINT fk_webmail_deleted_owner FOREIGN KEY(owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
ALTER TABLE mailbox ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT '2000-01-01 00:00:00' NOT NULL;
ALTER TABLE mail_alias ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT '2000-01-01 00:00:00' NOT NULL;
