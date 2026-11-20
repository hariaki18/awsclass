CREATE SEQUENCE department_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE employee_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE department (
  id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL,
  name_key VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(500), active BOOLEAN NOT NULL
);
CREATE TABLE employee (
  id BIGINT PRIMARY KEY, employee_code VARCHAR(32) NOT NULL UNIQUE,
  first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100), email VARCHAR(254) NOT NULL UNIQUE,
  phone VARCHAR(32), job_title VARCHAR(100), joining_date DATE NOT NULL,
  employment_type VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
  department_id BIGINT REFERENCES department(id),
  CONSTRAINT ck_employee_type CHECK(employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERN')),
  CONSTRAINT ck_employee_status CHECK(status IN ('ACTIVE','INACTIVE'))
);
ALTER TABLE app_user ADD COLUMN role_name VARCHAR(20) DEFAULT 'ADMIN' NOT NULL;
ALTER TABLE app_user ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE app_user ADD COLUMN employee_id BIGINT UNIQUE REFERENCES employee(id);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK(role_name IN ('ADMIN','HR_ADMIN','MAIL_ADMIN','EMPLOYEE'));
CREATE INDEX ix_employee_department ON employee(department_id);
