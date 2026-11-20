# Git handover

Extract this ZIP and open its XpertsEdgeHrms folder (the folder containing .gitignore, frontend and backend). Commit the extracted source files, not the ZIP itself.

For a new empty repository:

```powershell
git init
git add .
git status
git diff --cached --stat
git commit -m "Add XpertsEdge HRMS and local Webmail"
git branch -M main
git remote add origin <YOUR_REPOSITORY_URL>
git push -u origin main
```

Replace the repository URL placeholder. If you already have a Git repository, use that checkout; inspect `git status` and `git remote -v`, and do not re-add an existing origin or overwrite remote history. No force push is needed for this handover. Do not include real Google OAuth JSON credentials, API keys, .env secrets, database records/dumps, logs or runtime signing keys. .gitignore excludes common local dependencies/builds/runtime data and credential files; it cannot remove secrets already committed to Git history. .env.example intentionally contains blank secret placeholders.

Install/run after cloning:

```powershell
# Terminal 1: project root, after local MySQL setup
.\start-local-mail.ps1

# Terminal 2: project root
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

MySQL must be set up separately on each PC. The source archive does not contain existing users/messages/database records. A fresh database has bootstrap HRMS credentials documented in AUTH-SETUP.md; rotate local demo credentials before production. Mailboxes must be created in HRMS Mail Admin before mailbox login. Source .env files are not automatically loaded by Spring Boot.

- Webmail: http://127.0.0.1:5173/
- HRMS: http://127.0.0.1:5173/#/home
- API contracts: API.md
- Every backend source file/API: BACKEND-COMPLETE-GUIDE.md
- Local SMTP/startup/limits: LOCAL-MAIL.md
- Verification: TEST-RESULTS.md and verification-results.json (recorded checks, not a fresh production certification)

Included: Webmail default entry, inbox checkbox/row layout fix, implemented local modules and source tests. Pending: real provider provisioning, Google OAuth client/callback/token implementation, incoming Gmail sync, production Webmail design, Docker/Nginx deployment setup and advanced HR modules. Google Cloud project/consent setup alone does not connect Gmail. Local-mail is a development mode and refuses combination with prod.
