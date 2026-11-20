# MySQL setup

Status: MySQL is now the default required application database; missing MySQL settings fail startup instead of creating an H2 file. Local MySQL connection, migration and application startup were confirmed in the 5 November logs. Full MySQL CRUD/integration verification remains pending. H2 is retained for isolated automated tests and historical migration checks only. Existing H2 files are untouched; records are not automatically copied into MySQL.

## Windows local development

1. Install MySQL Community Server 8.4 using the Windows MSI and its Configurator: https://dev.mysql.com/downloads/mysql/8.4.html . Configure port 3306, a Windows service and a private root password. Workbench is optional.
2. Open the MySQL client as root (password prompt, do not put the password in the command):

   ```powershell
   & 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -u root -p
   ```

3. Run these statements in that MySQL client. Replace the password locally with a strong unique value; do not commit or share it in chat. Use an empty database. Never point the migration at an unrelated application's schema.

   ```sql
   CREATE DATABASE xpertsedgehrms CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
   CREATE USER 'hrms_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_A_PRIVATE_PASSWORD';
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
     ON xpertsedgehrms.* TO 'hrms_app'@'localhost';
   ```

4. Stop the previous HRMS backend with Ctrl+C, then run from the updated project root:

   ```powershell
   .\start-mysql.ps1
   ```

   Enter the application's MySQL password at the hidden prompt. This helper activates only `mysql` (local development), checks connectivity, and restores process environment values when it exits. It uses the current local app administrator settings; database credentials and app login credentials are separate. Do not use this helper for a remote production server.

5. Wait for successful Flyway migration, Hibernate schema validation, and `Started XpertsEdgeHrmsApplication`. Check:

   ```powershell
   Invoke-RestMethod http://localhost:8080/actuator/health/readiness
   ```

   In MySQL:

   ```sql
   USE xpertsedgehrms;
   SHOW TABLES;
   SELECT version, success FROM flyway_schema_history;
   SELECT id, email, role_name, active FROM app_user;
   ```

6. Start the frontend normally; test login, department/employee/user CRUD, mailbox/alias CRUD and notifications, then restart the backend and check persistence. Do not claim the transition is verified until these checks pass against MySQL.

## Deployment environment

Inject these alongside the existing production authentication variables:

```text
SPRING_PROFILES_ACTIVE=prod,mysql
MYSQL_URL=jdbc:mysql://db.example.com:3306/xpertsedgehrms?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
MYSQL_USERNAME=hrms_app
MYSQL_PASSWORD=<secret-manager-value>
```

Remove old `SPRING_DATASOURCE_*` overrides: they take precedence over profile configuration. Configure a trusted CA/certificate for VERIFY_IDENTITY. The server and database must already exist. Do not use the root account. Flyway runs DDL at startup with the listed schema-scoped migration permissions; a separate migration principal can be introduced for stricter deployments. Keep `ddl-auto=validate`, baseline disabled, and H2/MySQL migration histories separate. MySQL DDL is not transactional; back up before upgrades and inspect a failed migration before repair/retry. Do not enable `allowPublicKeyRetrieval` as a workaround for a remote TLS problem.

MySQL schema requires 8.0.16+ for enforced CHECK constraints; use the 8.4 LTS installer for this setup. Preserve UTC connection settings. `read` is a MySQL keyword, so the MySQL profile enables Hibernate keyword quoting. Hibernate AUTO identifiers use per-entity `*_seq` tables on MySQL; do not remove them.

## Dedicated database verification

Create a separate EMPTY `xpertsedgehrms_test` database/user with the same schema permissions. In a terminal, set `MYSQL_TEST_URL`, `MYSQL_TEST_USERNAME`, `MYSQL_TEST_PASSWORD` (use a secure local secret prompt), then run from `backend`:

```powershell
.\mvnw.cmd '-Dtest=MySqlSchemaTest' test
```

This test is skipped without MYSQL_TEST_URL. It creates the schema, validates Hibernate and tests generated IDs, foreign keys and notification timestamps/read updates. It rolls back test records; Flyway schema/admin bootstrap remain in the dedicated test DB. Never use a production database. Default H2 tests remain separate and do not prove MySQL compatibility.

## Existing H2 records

Switching profiles starts a separate database; it does not migrate existing users or business data. Keep a cold backup of `backend/data/xpertsedgehrms.mv.db` with all H2 writers stopped. A reviewed export/import must preserve IDs, password hashes, department/user links, aliases and UTC timestamps, reset MySQL sequence-emulation counters above imported maximum IDs, omit expired sessions/lockouts, compare row counts and constraints, and verify login/CRUD before cutover. No automatic data-transfer script is included yet. Never delete the H2 source during setup.

The Desktop project is a different copy. These changes are in the Documents/ChatGPT/HRMS workspace; run this updated copy or deliberately transfer the updated source before using these instructions.
