-- Invalid legacy rows deliberately stop adoption; they are never silently removed.
ALTER TABLE mailbox ADD CONSTRAINT ck_mailbox_quota CHECK (quota_gb BETWEEN 1 AND 1000);
ALTER TABLE mailbox ADD CONSTRAINT ck_mailbox_usage CHECK (used_gb >= 0);
ALTER TABLE mail_alias ADD CONSTRAINT fk_alias_mailbox FOREIGN KEY (target) REFERENCES mailbox(address);
