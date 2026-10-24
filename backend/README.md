# Student Organization Operations Portal — Backend

## Tech stack
| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, Actuator) |
| API gateway | Spring Cloud Gateway |
| Build | Maven multi-module with the Maven wrapper |
| Auth | JWT (jjwt) and BCrypt password hashing |
| Database | PostgreSQL on Supabase; H2 in PostgreSQL mode for local runs and tests |
| Service-to-service calls | Spring `RestClient` |
| API docs | springdoc-openapi (Swagger UI) |
| Boilerplate | Lombok |
| Tests | JUnit 5, Mockito, MockMvc, JaCoCo (coverage) |
| CI | GitHub Actions |

## Prerequisites
- Java 21. Maven comes with the wrapper (`./mvnw`), so nothing else needs installing.

## Setup
```bash
cd backend
cp .env.example .env
```
Fill in `JWT_SECRET` (32+ characters), `INTERNAL_SERVICE_TOKEN`, `SEED_ADMIN_EMAIL` and `SEED_ADMIN_PASSWORD`.
Never commit `.env`.

**Database (Supabase):**
1. In `.env`, set `DB_URL`, `DB_USER` and `DB_PASSWORD` from Supabase (transaction pooler, port 6543).
2. `DB_URL` must end with `?sslmode=require&stringtype=unspecified` and be wrapped in single quotes.
3. Start with `SPRING_PROFILES_ACTIVE=supabase ./run-all.sh`.

## Run
```bash
./run-all.sh    # build and start all services (logs in backend/logs/)
./stop-all.sh   # stop them
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Seed admin: created on first start from `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` if no admin exists.
  Log in with `POST /auth/login`, then click **Authorize** and paste the token.
- OpenAPI files: `docs/api/` (refresh with `./export-openapi.sh` while the services are running).

## Quick reference for the frontend
| Item | Value |
|---|---|
| Java | 21 (for example Temurin 21) |
| Other installs | Git only (no Maven, Docker or database needed) |
| Windows | Run the scripts in Git Bash or WSL |
| Start / stop | `./run-all.sh` / `./stop-all.sh` |
| Health check | http://localhost:8080/actuator/health shows `UP` |
| API base URL | `http://localhost:8080/api` (always call the gateway, not ports 8081–8086) |
| CORS allowed | `http://localhost:5173`, `http://localhost:3000` |
| Login | `POST /api/auth/login` with `{ "email", "password" }` |
| Login response | `{ accessToken, tokenType, expiresIn, user }` |
| Auth header | `Authorization: Bearer <accessToken>` |
| Token expiry | 2 hours, then requests return 401 |
| First admin | The seed admin from `.env` (officer_admin) |
| New members | Register, then an admin approves them via `/api/memberships` |
| Errors | JSON (RFC 7807) with a `detail` message to show the user |
| API docs | http://localhost:8080/swagger-ui.html |
| Logs | `backend/logs/<service>.log` |
| Reset local data | Stop the services, then delete `backend/data/` |

## Tests
```bash
./mvnw verify
```
Tests use in-memory H2, never Supabase. The build fails if line coverage of any `service` or
`controller` package is below 70%. Reports: `<module>/target/site/jacoco/index.html`.

## Services and table ownership
| Service | Port | Owns tables |
|---|---|---|
| gateway | 8080 | none |
| user-service | 8081 | `app_user`, `membership_application` |
| event-service | 8082 | `event`, `rsvp`, `attendance` |
| task-service | 8083 | `committee`, `committee_member`, `task` |
| content-service | 8084 | `announcement`, `document_link` |
| notification-service | 8085 | `notification` |
| reporting-service | 8086 | none (read-only reports) |
| ai-service | 8087 | `ai_request_log` (meeting-notes summary; see `ai-service/README.md`) |

Any service may insert into `audit_event`.
