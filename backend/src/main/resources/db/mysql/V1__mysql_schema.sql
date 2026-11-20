-- MySQL 8.0.16+ (CHECK constraints enforced). Use an empty, dedicated database.
SET SESSION default_storage_engine=InnoDB;
-- Hibernate AUTO uses sequence-emulation tables on MySQL, with allocation size 50.
CREATE TABLE app_user_seq (next_val BIGINT);
INSERT INTO app_user_seq VALUES (1);
CREATE TABLE mailbox_seq (next_val BIGINT);
INSERT INTO mailbox_seq VALUES (1);
CREATE TABLE mail_alias_seq (next_val BIGINT);
INSERT INTO mail_alias_seq VALUES (1);
CREATE TABLE app_notification_seq (next_val BIGINT);
INSERT INTO app_notification_seq VALUES (1);
CREATE TABLE department_seq (next_val BIGINT);
INSERT INTO department_seq VALUES (1);
CREATE TABLE employee_seq (next_val BIGINT);
INSERT INTO employee_seq VALUES (1);

CREATE TABLE department (
  id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL,
  name_key VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(500), active BOOLEAN NOT NULL
);
CREATE TABLE employee (
  id BIGINT PRIMARY KEY, employee_code VARCHAR(32) NOT NULL UNIQUE,
  first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100), email VARCHAR(254) NOT NULL UNIQUE,
  phone VARCHAR(32), job_title VARCHAR(100), joining_date DATE NOT NULL,
  employment_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
  department_id BIGINT,
  CONSTRAINT fk_employee_department FOREIGN KEY (department_id) REFERENCES department(id),
  CONSTRAINT ck_employee_type CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERN')),
  CONSTRAINT ck_employee_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
CREATE TABLE app_user (
  id BIGINT PRIMARY KEY, email VARCHAR(255) UNIQUE, name VARCHAR(255), password_hash VARCHAR(255),
  role_name VARCHAR(20) DEFAULT 'ADMIN' NOT NULL, active BOOLEAN DEFAULT TRUE NOT NULL,
  employee_id BIGINT UNIQUE,
  CONSTRAINT fk_user_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
  CONSTRAINT ck_app_user_role CHECK (role_name IN ('ADMIN','HR_ADMIN','MAIL_ADMIN','EMPLOYEE'))
);
CREATE TABLE mailbox (
  id BIGINT PRIMARY KEY, active BOOLEAN NOT NULL, address VARCHAR(255) UNIQUE,
  display_name VARCHAR(255), password_hash VARCHAR(255), quota_gb INTEGER NOT NULL,
  used_gb DOUBLE PRECISION NOT NULL,
  CONSTRAINT ck_mailbox_quota CHECK (quota_gb BETWEEN 1 AND 1000),
  CONSTRAINT ck_mailbox_usage CHECK (used_gb >= 0)
);
CREATE TABLE mail_alias (
  id BIGINT PRIMARY KEY, alias_name VARCHAR(255) UNIQUE, target VARCHAR(255),
  CONSTRAINT fk_alias_mailbox FOREIGN KEY (target) REFERENCES mailbox(address)
);
CREATE TABLE app_notification (
  id BIGINT PRIMARY KEY, body VARCHAR(255), created_at TIMESTAMP(6) NULL,
  `read` BOOLEAN NOT NULL, recipient VARCHAR(255) NOT NULL, title VARCHAR(255)
);
CREATE TABLE auth_session (
  id VARCHAR(255) PRIMARY KEY, email VARCHAR(255) NOT NULL, expires_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE login_attempt (
  email VARCHAR(255) PRIMARY KEY, failures INTEGER NOT NULL, window_start TIMESTAMP(6) NULL
);
CREATE TABLE mail_namespace (id BIGINT PRIMARY KEY);
CREATE INDEX ix_notification_recipient_time ON app_notification(recipient, created_at, id);
CREATE INDEX ix_notification_recipient_read ON app_notification(recipient, `read`);
CREATE INDEX ix_session_email ON auth_session(email);
CREATE INDEX ix_session_expiry ON auth_session(expires_at);
CREATE INDEX ix_alias_target ON mail_alias(target);
CREATE INDEX ix_employee_department ON employee(department_id);
