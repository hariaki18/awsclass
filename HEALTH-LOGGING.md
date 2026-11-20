# Health checks and application logs

## Probe URLs

Use the backend port (8080 locally), not the Vite frontend port:

| URL | Purpose |
|---|---|
| `/actuator/health` | Overall application health |
| `/actuator/health/liveness` | Process availability; deliberately excludes database health |
| `/actuator/health/readiness` | Accepting traffic and database connectivity |

Probes are anonymous and show only public status/group information, never database details, credentials or internal errors. Healthy probes return 200. A down database makes readiness 503 while liveness remains 200 if the application process is healthy. Refusing traffic or broken liveness returns 503 for the corresponding probe.

Only the health Actuator endpoint is exposed. Discovery links, env, configprops, heapdump, loggers and JMX exposure are disabled. DevOps should route probes to the backend/internal network and avoid publishing other management endpoints.

Graceful shutdown is enabled with a 20-second shutdown phase timeout. The deployment platform should respect readiness state and allow enough termination time for in-flight requests.

## Application request logs

Each request gets a generated UUID in `X-Request-ID`. Application request-completion logs include that ID through MDC, HTTP method, matched route template, status code and elapsed milliseconds.

Successful requests log at INFO, rejected/invalid requests at WARN, and server failures at ERROR. Successful health probes are omitted to avoid poll noise; unhealthy probes are logged. Startup/migration logs remain available.

Application request logging never records raw URLs, query values, request/response bodies, Authorization headers, cookies, passwords or API keys. Raw unmatched paths are logged as `unmatched`. Client-provided request IDs are not trusted or echoed. Do not enable framework request-detail/SQL-parameter debug logging or put credentials into database URLs.

Default output: console and `backend/logs/xpertsedge-hrms.log` when run from `backend`. Configure `LOG_DIR` to use a mounted log directory. Files rotate at 10 MB, retain up to 14 days and cap archived files at 200 MB. Active files may use additional space. Restrict log access; forwarding, alerts and centralized retention are DevOps responsibilities.

## Verification

Seven JUnit tests cover public healthy responses, DB-down readiness versus liveness, refusing-traffic/broken-liveness states, management exposure restrictions, request ID generation/MDC cleanup, and sentinel password/token/cookie/query redaction. They run as part of `npm run verify`.

The DB-down check uses an isolated test health contributor. It does not shut down or damage the local database. Logs were checked with disposable sentinel values, not real secrets.

Live smoke checks on 29 October 2026: overall health, liveness and readiness each returned HTTP 200. An anonymous `/api/me` request returned 401 with a generated `X-Request-ID`, and the exact correlation ID was found in the rolling application log beside the `/api/me` route/status/duration entry.

The final full `npm run verify` pipeline passed all seven stages, including 8 frontend tests, 17 backend/migration/health tests, frontend/backend builds and isolated Mail/Auth/password/production-guard regressions. See `verification-results.json` for the latest machine-readable status.

Reference: [Spring Boot Actuator endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).
