# Student Organization Operations Portal

A web portal for running a student organization: membership applications and approval, roles, events with
RSVPs and attendance, committees and tasks, announcements and shared documents, in-app notifications,
engagement reports, and an AI meeting-notes summary.

## Repository structure
```
student-orgs/
├── backend/     Spring Boot microservices and API gateway (see backend/README.md)
├── frontend/    React + TypeScript web app (see frontend/README.md)
├── docs/        Test document, AI evaluation, API docs and screenshots
└── .github/     CI workflow (backend build and tests)
```

## Tech stack
| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Cloud Gateway, JWT, Maven |
| Frontend | React 18, TypeScript, Vite |
| Database | PostgreSQL on Supabase (H2 for local runs and tests) |
| AI | Ollama (llama3.2:3b), local model |
| Tests and CI | JUnit 5, Mockito, MockMvc, JaCoCo, GitHub Actions |

## How it fits together
```
Browser ──► Frontend (Vite, :5173) ──► Gateway :8080 (/api) ──► Backend services :8081–8087 ──► PostgreSQL
                                                                      └─ ai-service ──► Ollama
```

## Quick start
**Prerequisites:** Java 21, Node.js (for the frontend), Git. Ollama only for the AI summary.

1. **Backend**
   ```bash
   cd backend
   cp .env.example .env    # fill in the values (see backend/README.md)
   ./run-all.sh            # stop with ./stop-all.sh
   ```
2. **Frontend** (in a second terminal)
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
3. Open http://localhost:5173. Swagger UI for the API: http://localhost:8080/swagger-ui.html

Full setup details (Supabase, AI summary, tests) are in [backend/README.md](backend/README.md) and
[frontend/README.md](frontend/README.md).

## Documentation
| Document | Contents |
|---|---|
| [backend/README.md](backend/README.md) | Backend architecture, setup, run and test steps |
| [frontend/README.md](frontend/README.md) | Frontend setup and features |
| [docs/test-document.md](docs/test-document.md) | Backend test cases and results |
| [docs/AI_FEATURE_EVALUATION.md](docs/AI_FEATURE_EVALUATION.md) | AI summary implementation and evaluation |
| [docs/api/](docs/api/) | OpenAPI (Swagger) files, one per service |
