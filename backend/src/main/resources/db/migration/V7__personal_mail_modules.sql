ALTER TABLE webmail_message ADD COLUMN scheduled_at TIMESTAMP(6) WITH TIME ZONE NULL;
CREATE TABLE mail_contact (
 id VARCHAR(255) PRIMARY KEY,owner_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL,phone VARCHAR(32) NOT NULL,company VARCHAR(100) NOT NULL,notes VARCHAR(1000) NOT NULL,
 CONSTRAINT fk_contact_owner FOREIGN KEY(owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
CREATE INDEX ix_contact_owner ON mail_contact(owner_id);
CREATE TABLE mail_calendar_event (
 id VARCHAR(255) PRIMARY KEY,owner_id BIGINT NOT NULL,title VARCHAR(150) NOT NULL,
 starts_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,ends_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,location VARCHAR(200) NOT NULL,notes VARCHAR(2000) NOT NULL,
 CONSTRAINT fk_calendar_owner FOREIGN KEY(owner_id) REFERENCES mailbox(id) ON DELETE CASCADE,
 CONSTRAINT ck_calendar_dates CHECK(ends_at>starts_at)
);
CREATE INDEX ix_calendar_owner ON mail_calendar_event(owner_id,starts_at);
CREATE TABLE mail_preference (
 owner_id BIGINT PRIMARY KEY,signature VARCHAR(2000) NOT NULL,theme VARCHAR(15) NOT NULL,
 density VARCHAR(15) NOT NULL,time_zone VARCHAR(60) NOT NULL,shortcuts BOOLEAN NOT NULL,
 CONSTRAINT fk_preference_owner FOREIGN KEY(owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
CREATE TABLE mail_security (
 owner_id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL,secret_cipher VARCHAR(255),pending_cipher VARCHAR(255),
 pending_expires TIMESTAMP(6) WITH TIME ZONE NULL,last_counter BIGINT NOT NULL,recovery_hashes CLOB,
 CONSTRAINT fk_security_owner FOREIGN KEY(owner_id) REFERENCES mailbox(id) ON DELETE CASCADE
);
CREATE INDEX ix_mail_scheduled ON webmail_message(folder,scheduled_at);
