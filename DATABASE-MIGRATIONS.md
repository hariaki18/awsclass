# Database migrations and upgrade procedure

The current application uses H2 2.2.224. Flyway now owns schema changes; Hibernate uses `ddl-auto=validate`, not `update`. These migrations have been verified against H2, including the actual local database backup. PostgreSQL/MySQL migration and compatibility verification are not included.

## Versioned migrations

| Version | Purpose |
|---|---|
| V1 | Existing application tables and Hibernate-compatible sequences (allocation size 50) |
| V2 | Notification recipient/time/read, session email/expiry, and alias target indexes |
| V3 | Mailbox quota/usage checks and alias-to-mailbox foreign key |
| V4 | Basic employee/department tables, account roles/active status and employee links |

Files live in `backend/src/main/resources/db/migration/`. Flyway records applied versions/checksums in `flyway_schema_history`. Never edit a migration after it has been deployed; add V4, V5, etc. instead. Migration clean is disabled.

## Fresh installation

Start the packaged backend normally. An empty database automatically receives V1–V4 before Hibernate validates the entities and the administrator is bootstrapped. No baseline flag is needed.

## Adopt an existing pre-Flyway database

1. Stop all backend processes using that database. Keep the previous application artifact available.
2. Make a verified offline backup of the H2 `.mv.db` file. Preserve the signing key separately and securely; do not commit/distribute database files, passwords or keys.
3. Rehearse the upgrade on a copy. Verify counts, password hashes, IDs, sequence positions, and application login/CRUD behavior.
4. For the first upgraded startup only, set `DB_ADOPT_EXISTING=true` or pass `--spring.flyway.baseline-on-migrate=true`.
5. Flyway baselines the legacy schema at version **0**, then runs V1–V4. V1 preserves existing table/sequence objects; existing rows and sequence positions are retained.
6. After successful migration, stop and restart without the adoption override. History validation now runs normally and no already-applied migration is rerun.

Default adoption is **false**. An untracked non-empty database refuses startup rather than being silently baselined. Verify the database URL before enabling adoption. This follows [Flyway's documented baseline behavior](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting).

### Pre-upgrade data checks

These queries should return no rows before V3:

```sql
SELECT id, quota_gb, used_gb FROM mailbox
WHERE quota_gb NOT BETWEEN 1 AND 1000 OR used_gb < 0;

SELECT a.id, a.target FROM mail_alias a
LEFT JOIN mailbox m ON m.address = a.target
WHERE a.target IS NOT NULL AND m.id IS NULL;
```

Invalid legacy data stops migration. Rows are never automatically deleted or rewritten to bypass a constraint. Resolve data issues explicitly and rehearse again.

### Recovery

H2 DDL migrations are not fully transactional; a failed migration may leave partial schema changes. Do not blindly run Flyway repair/clean or retry against the only copy. Stop the backend, preserve the failure logs/database for diagnosis, restore the verified pre-upgrade database backup, and use the matching previous application artifact. This project provides forward migrations, not destructive down migrations.

## Automated tests

- `MigrationTest`: fresh schema, repeat startup, explicit legacy adoption, password/data/sequence preservation, original-zip schema upgrade, invalid/orphan legacy data rejection, database integrity, checksum rejection, and disabled clean.
- `ApplicationSchemaTest`: Spring bootstraps against a migrated empty database and Hibernate validates its mappings.
- `ExistingDatabaseMigrationTest`: optional real-backup rehearsal. It copies the backup to a disposable temporary database, migrates the copy, and compares SHA-256 row fingerprints and sequence positions. The source backup is never modified.

To include the real-backup check in the full verification pipeline, set `MIGRATION_BACKUP` to the absolute backup `.mv.db` path, then run `npm run verify` from the project root. Without that variable, synthetic migration tests still run, while the optional real-backup test is skipped.

The local pre-upgrade backup for this handover was created at `.backups/before-flyway-20261029-115202/xpertsedgehrms.mv.db` and its file hash was checked against the stopped source database. Keep that folder local and secure.


Current local mail schema: MySQL V3 adds To/Cc/reply addressing, message status/original folder, attachment bytes, capture deletion tombstones and mailbox/alias activation timestamps; historical H2 tests use V6. Existing checksums were not edited. Back up before migration. These timestamps prevent a recreated account/retargeted alias importing older captures.



Local mail personal modules add MySQL `V4__personal_mail_modules.sql` (isolated H2 `V7__personal_mail_modules.sql`): nullable scheduled timestamp/index plus owner-linked contact, calendar, preference and encrypted MFA tables. Existing messages/accounts are preserved. Applied migrations must not be edited. A pre-migration local MySQL dump was taken under `.backups/before-personal-mail-*`; runtime migration and Hibernate schema validation passed on MySQL 8.4.

Gmail API/OAuth adds MySQL `V5__gmail_oauth.sql` (isolated H2 `V8__gmail_oauth.sql`): `gmail_account` (one row per linked mailbox, encrypted refresh token, Gmail history id, last sync) and nullable `gmail_message_id`/`gmail_thread_id` columns on `webmail_message`. Existing rows are untouched; see GMAIL-OAUTH.md.
