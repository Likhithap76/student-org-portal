# Student Organization Portal frontend

React 18 + TypeScript + Vite frontend added by Rhea's frontend workstream for Team 7. This folder is independent of the existing Java 21 Spring Boot services.

## Run locally

1. Start the existing backend from `../backend`: `./run-all.sh`. Follow its `.env.example` and database setup instructions first.
2. In this `frontend` directory, run `npm install` and then `npm run dev`.
3. Open <http://localhost:5173>.

The Vite server proxies `/api` to `http://localhost:8080`; you can override the frontend API base with `VITE_API_BASE_URL` if necessary. Swagger remains at <http://localhost:8080/swagger-ui.html>.

## Integrated features

- Register and login using `POST /api/auth/register` and `POST /api/auth/login`.
- Display member application status; `OFFICER_ADMIN` approval/rejection and officer-only roster.
- Browse/create events and update RSVP.
- View committees and assigned tasks; officers can create committees and tasks.
- Browse/publish announcements and document links.
- Notifications, summary reporting, and an AI summary interface scaffold.

The AI summary button is **intentionally not connected**. Likhitha stated on October 23 that the endpoint was not pushed and no LLM was integrated/tested. The UI never fabricates AI-generated output. Connect the real endpoint after the team specifies and verifies its request/response contract.

## First integration test

1. Use a seeded officer/admin account to log in.
2. In a second browser, register a new member and confirm the registration success message.
3. Log in as the officer, open **Membership**, and approve the new application.
4. Refresh the officer roster. Verify that the new member is listed.
5. Log in as the approved member. Confirm member-only navigation and roster visibility.

The app uses in-memory bearer tokens, which intentionally require a new login after reload. This is a course demo, not production authentication/session hardening.

## Current verification status

Source/API contracts were inspected against the October 23 OpenAPI exports. Runtime browser-to-backend integration has **not** been executed in the authoring environment. Record actual requests, responses, database persistence and screenshots before claiming end-to-end completion in the progress report.
