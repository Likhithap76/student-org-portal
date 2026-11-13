# Student Org Portal: Backend Test Document

As of 11 Nov 2026

## Purpose and scope

The backend passes 525 automated tests, and all 60 manual test cases passed against the real Supabase database.

This document covers the backend of the Student Organization Operations Portal only: the gateway and the seven services (user, event, task, content, notification, reporting, ai). The frontend is out of scope. The AI summary uses a local Ollama model (llama3.2:3b); its output quality is evaluated separately in `docs/AI_FEATURE_EVALUATION.md`.

## Test environment

| Item | Automated tests | Manual system tests |
| --- | --- | --- |
| Database | In-memory H2 (PostgreSQL mode), fresh per run | Supabase PostgreSQL, transaction pooler (port 6543) |
| Profile | `test` | `supabase` |
| How run | `./mvnw verify` (JUnit 5, Mockito, MockMvc) | `./run-all.sh`, then Swagger UI at `localhost:8080/swagger-ui.html` |
| Services | One service per test run | All 8 apps running (gateway + 7 services) |
| AI | LLM answer mocked (Mockito) and a local test HTTP server | Local Ollama (llama3.2:3b) |
| Stack | Java 21, Spring Boot 3.3, Spring Cloud Gateway | same |

## Test approach

1. **Automated tests** run on every build and in CI (GitHub Actions):
    1. Unit tests: every business rule in the service layer, with other services mocked.
    2. Controller tests: status codes, validation errors, and 401 / 403 for each endpoint.
    3. Integration tests: end-to-end flows per service on H2.
    4. Coverage gate: the build fails if any service or controller package is below 70% line coverage.
2. **Manual system tests** on Supabase, through the gateway in Swagger, one scenario at a time. Each case checks the HTTP status and response, and where data is written, the rows in the Supabase Table Editor.

Negative cases (wrong role, bad input, rule broken) count as passed when the backend refuses them with the expected code.

## Test accounts and data

All test data uses names starting with TEST or emails at test.com, so it can be found and removed from the shared database. Passwords are not recorded here.

| Account / data | Used as |
| --- | --- |
| Officer_admin test account | Approves members, manages events, tasks, content, reports, AI |
| Member test account | Approved, active member |
| Applicant test account | Rejected, then reapplied (stays pending) |
| TEST event, committee, task, announcement, document | Created during the tests |

## Manual test cases (Supabase)

All 60 cases passed (last run 11 Nov 2026).

### Setup, membership and accounts

| ID | Area | Scenario | Expected | Status |
| --- | --- | --- | --- | --- |
| SC-01 | Setup | All services start against Supabase and pass the schema check | All 8 apps UP, database UP, tables match the code | Passed |
| MB-01 | Membership | Register a new user | 201, MEMBER, PENDING | Passed |
| MB-02 | Membership | Register with an email that already exists | 409 | Passed |
| MB-03 | Membership | Register with a weak password | 400 | Passed |
| MB-04 | Membership | Pending user logs in | 200, status PENDING | Passed |
| MB-05 | Membership | Pending user opens a member-only feature | 403 | Passed |
| MB-06 | Membership | Pending user views own application | 200, decision PENDING | Passed |
| MB-07 | Membership | Officer_admin opens the review queue | 200, pending applications listed, oldest first | Passed |
| MB-08 | Membership | Officer_admin approves an application | 200 APPROVED; user active; audit row | Passed |
| MB-09 | Membership | Approve an application that is already decided | 422 | Passed |
| MB-10 | Membership | Approved user logs in again | 200, status ACTIVE | Passed |
| MB-11 | Membership | Member opens the review queue | 403 | Passed |
| MB-12 | Membership | Officer_admin rejects an application | 200 REJECTED; user stays pending; audit row | Passed |
| MB-13 | Membership | Rejected applicant views own application | decision REJECTED | Passed |
| MB-14 | Membership | Rejected applicant reapplies | 201, new PENDING application | Passed |
| MB-15 | Membership | Applicant reapplies while one is pending | 409 | Passed |
| AC-01 | Accounts | Officer_admin opens the roster | 200 with name, email and role | Passed |
| AC-02 | Accounts | Member opens the roster | 403 (officer_admin only) | Passed |
| AC-03 | Accounts | Officer_admin promotes a member to officer_admin | 200 | Passed |
| AC-04 | Accounts | Officer_admin demotes an officer_admin to member | 200 | Passed |
| AC-05 | Accounts | Officer_admin deactivates a member | 200, INACTIVE | Passed |
| AC-06 | Accounts | Inactive user logs in | 403 | Passed |
| AC-07 | Accounts | Deactivate a user who is already inactive | 422 | Passed |
| AC-08 | Accounts | Officer_admin reactivates an inactive user | 200, ACTIVE | Passed |
| AC-09 | Accounts | Reactivated user logs in | 200 | Passed |
| AC-10 | Accounts | Officer_admin deactivates own account | 422 | Passed |
| AC-11 | Accounts | Member deactivates a user or changes a role | 403 | Passed |

### Events, tasks, content, reports and AI

| ID | Area | Scenario | Expected | Status |
| --- | --- | --- | --- | --- |
| EV-01 | Events | Officer_admin creates an event starting in the future | 201, SCHEDULED | Passed |
| EV-02 | Events | Create an event with a start in the past | 422 | Passed |
| EV-03 | Events | Member RSVPs GOING before the start | 200 | Passed |
| EV-04 | Events | Member RSVPs MAYBE | 400 | Passed |
| EV-05 | Events | Member lists upcoming events | goingCount and own response shown | Passed |
| EV-06 | Events | Pending user RSVPs | 403 | Passed |
| EV-07 | Events | Officer_admin records attendance after the start | 200, attendance rate = present / going | Passed |
| EV-08 | Events | Member RSVPs after the event started | 422 | Passed |
| TK-01 | Committees & tasks | Officer_admin creates a committee | 201 | Passed |
| TK-02 | Committees & tasks | Create a committee with an existing name (any case) | 409 | Passed |
| TK-03 | Committees & tasks | Add an active member to the committee | 200 | Passed |
| TK-04 | Committees & tasks | Add a pending user to the committee | 422 | Passed |
| TK-05 | Committees & tasks | Assign a task to a committee member | 201, TODO; assignee notified | Passed |
| TK-06 | Committees & tasks | Assign a task to someone outside the committee | 422 | Passed |
| TK-07 | Committees & tasks | Assignee moves a task from TODO straight to DONE | 422 | Passed |
| TK-08 | Committees & tasks | Assignee moves a task to IN_PROGRESS, then DONE | 200, completedAt set | Passed |
| TK-09 | Committees & tasks | Assignee reopens a done task | 200, completedAt cleared | Passed |
| TK-10 | Committees & tasks | Another member changes the task's status | 403 | Passed |
| TK-11 | Committees & tasks | Remove a member who still has an open task | 422 | Passed |
| TK-12 | Committees & tasks | Member removes a committee member | 403 | Passed |
| CN-01 | Content & notifications | Officer_admin posts an announcement | 201; one notification per active user | Passed |
| CN-02 | Content & notifications | Member lists own notifications | Announcement and task notifications shown | Passed |
| CN-03 | Content & notifications | Member marks one notification read | 200, read true | Passed |
| CN-04 | Content & notifications | Member marks all notifications read | Unread count 0 | Passed |
| CN-05 | Content & notifications | Save a document with an http:// URL | 422 | Passed |
| CN-06 | Content & notifications | Save a document with an https:// URL | 201 | Passed |
| CN-07 | Content & notifications | Search documents by category | Matching document listed | Passed |
| RP-01 | Reports | Officer_admin opens the summary | Totals match the Supabase tables | Passed |
| RP-02 | Reports | Engagement report after a member completes a task | That member's tasksCompleted goes up by 1 | Passed |
| RP-03 | Reports | Download the engagement report as CSV | One row per active member | Passed |
| RP-04 | Reports | Member opens a report | 403 | Passed |
| AI-01 | AI summary | Officer_admin summarizes meeting notes with the local LLM (Ollama) | 200 with summary, decisions and suggested action items; log row without text | Passed |
| AI-02 | AI summary | Member calls the AI summary | 403 (officer_admin only) | Passed |

## Automated test summary

All 525 automated tests pass, and every service and controller package is at 100% line coverage (gate: 70%).

| Module | Tests | Line coverage, whole module |
| --- | --- | --- |
| user-service | 119 | 97% |
| task-service | 89 | 99% |
| common (shared library) | 75 | 93% |
| event-service | 74 | 99% |
| content-service | 49 | 97% |
| ai-service | 43 | 90% |
| reporting-service | 33 | 72% |
| notification-service | 24 | 96% |
| gateway | 19 | 92% |
| **Total** | **525** | |

reporting-service is lower overall because its SQL has no automated database test; it was verified manually on Supabase (RP-01 to RP-03).
