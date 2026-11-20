-- Same table/sequence names and allocation sizes as the pre-Flyway Hibernate schema.
-- IF NOT EXISTS permits explicit adoption at baseline version 0 without deleting data.
CREATE SEQUENCE IF NOT EXISTS app_user_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS mailbox_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS mail_alias_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS app_notification_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT PRIMARY KEY,
  email VARCHAR(255) UNIQUE,
  name VARCHAR(255),
  password_hash VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS mailbox (
  id BIGINT PRIMARY KEY,
  active BOOLEAN NOT NULL,
  address VARCHAR(255) UNIQUE,
  display_name VARCHAR(255),
  password_hash VARCHAR(255),
  quota_gb INTEGER NOT NULL,
  used_gb DOUBLE PRECISION NOT NULL
);
CREATE TABLE IF NOT EXISTS mail_alias (
  id BIGINT PRIMARY KEY,
  alias_name VARCHAR(255) UNIQUE,
  target VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS app_notification (
  id BIGINT PRIMARY KEY,
  body VARCHAR(255),
  created_at TIMESTAMP(6) WITH TIME ZONE,
  read BOOLEAN NOT NULL,
  recipient VARCHAR(255) NOT NULL,
  title VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS auth_session (
  id VARCHAR(255) PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS login_attempt (
  email VARCHAR(255) PRIMARY KEY,
  failures INTEGER NOT NULL,
  window_start TIMESTAMP(6) WITH TIME ZONE
);
CREATE TABLE IF NOT EXISTS mail_namespace (id BIGINT PRIMARY KEY);
