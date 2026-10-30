# Student Organization Operations Portal: Backend

The backend for the Student Organization Operations Portal. It handles membership,
roles, events and RSVPs, committees and tasks, announcements and documents, in-app notifications, reports,
and an AI meeting-notes summary.

It is built as **Spring Boot microservices**. The frontend talks only to the **gateway**, which checks the
login token and forwards each request to the right service.

```
Frontend ──► Gateway :8080 ──► user-service :8081
             (/api/**)     ├─► event-service :8082
                           ├─► task-service :8083
                           ├─► content-service :8084
                           ├─► notification-service :8085
                           ├─► reporting-service :8086
                           └─► ai-service :8087
                                      │
                         PostgreSQL (Supabase) / H2 (local)
```

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

## Project structure
```
backend/
├── common/                  Shared code (security, errors, clients)
├── gateway/                 API gateway
├── user-service/            Accounts, roles, membership
├── event-service/           Events, RSVPs, attendance
├── task-service/            Committees and tasks
├── content-service/         Announcements, documents
├── notification-service/    Notifications
├── reporting-service/       Reports
├── ai-service/              Meeting-notes summary
├── run-all.sh, stop-all.sh  Start / stop all services
└── pom.xml                  Maven build
```
Each service follows the same layout: `controller/`, `service/`, `repository/`, `domain/`, `dto/`.

## Prerequisites
- Java 21
- Git

Maven comes with the wrapper (`./mvnw`). On Windows, run the scripts in Git Bash or WSL.

## Setup
```bash
cd backend
cp .env.example .env
```
Fill in `JWT_SECRET` (32+ characters), `INTERNAL_SERVICE_TOKEN`, `SEED_ADMIN_EMAIL` and `SEED_ADMIN_PASSWORD`.

**Database:** by default the services use a local H2 database in `backend/data/`, so nothing else is needed.
To use Supabase:
1. In `.env`, set `DB_URL`, `DB_USER` and `DB_PASSWORD` from Supabase (transaction pooler, port 6543).
2. `DB_URL` must end with `?sslmode=require&stringtype=unspecified` and be wrapped in single quotes.
3. Start with `SPRING_PROFILES_ACTIVE=supabase ./run-all.sh`.

## Run
```bash
./run-all.sh    # build and start all services (logs in backend/logs/)
./stop-all.sh   # stop them
```

| What | Where |
|---|---|
| API base URL (for the frontend) | `http://localhost:8080/api` |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |
| API docs (JSON) | `docs/api/`, one file per service |

The first admin is created on first start from `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`. Log in with
`POST /api/auth/login` and send the returned token as `Authorization: Bearer <accessToken>`.

To run one service only: `./mvnw -pl user-service -am spring-boot:run`.

## Tests
```bash
./mvnw verify
```
Tests use in-memory H2, never Supabase. The build fails if line coverage of any `service` or
`controller` package is below 70%. Coverage reports: `<module>/target/site/jacoco/index.html`.
GitHub Actions runs the same command on every push to `backend/`.

## Services and tables
| Service | Port | Owns tables |
|---|---|---|
| gateway | 8080 | none |
| user-service | 8081 | `app_user`, `membership_application` |
| event-service | 8082 | `event`, `rsvp`, `attendance` |
| task-service | 8083 | `committee`, `committee_member`, `task` |
| content-service | 8084 | `announcement`, `document_link` |
| notification-service | 8085 | `notification` |
| reporting-service | 8086 | none (read-only reports) |
| ai-service | 8087 | `ai_request_log` |
