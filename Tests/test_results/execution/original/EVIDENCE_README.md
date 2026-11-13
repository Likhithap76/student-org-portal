# Regression evidence for 4a74d05

Execution: November 12, 2026 (America/Chicago). Source SHA: 4a74d05f89b1c4d837456cc06af4d85bd49c1045. All 358 files in supplied main (5).zip matched this commit. The source checkout was not modified.

## Outcome

- Maven verify: 525 tests, 0 failures, 0 errors, 0 skipped; coverage gates passed.
- Frontend npm ci and npm run build: passed.
- API logs: 91 initial checks (89 passed; 2 test-date setup errors), 82 follow-up checks passed, 11 restart checks passed. Total 182 passing recorded assertions, including data-fixture creation. The two erroneous date assertions were superseded by correct America/Chicago date and exact CSV reconciliation checks. These are not 182 independent user acceptance scenarios.
- Browser: 6 grouped checks passed and 3 mobile layout observations failed (one responsive-layout defect).
- Overall: PARTIAL WITH FAILURES. Live Ollama generation and timing, full browser timing, remaining officer UI cases, forced-401 redirect, full privacy audit and Supabase integration were not completed.

## Files

`api_regression.py`, `followup.py`, `restart_verify.py`: executed Python standard-library live API harnesses. The initial script retains the original UTC date assertions; followup.py corrects and documents them. JSON and logs record the actual executions without hiding the original harness errors.
`start_stack.py`: local service runner; uses a Java 21 installation in runtime/jdk-*/Contents/Home/bin/java, checkout ../student-org-portal, isolated ./data and synthetic-only credentials. All scripts assume they reside together in a regression directory adjacent to the checkout. Run against a fresh local database to avoid duplicate fixture names.
`junit-summary.json`: suite-level executed counts extracted from Maven Surefire XML.
`logs/maven-verify.log`: complete backend build/test/coverage output.
`logs/frontend-build.log`, `logs/npm-ci.log`: frontend installation/build output.
`ui-results.json` and `ui-*.png`: manual browser-tool checks/captures; no reusable Selenium suite was supplied or created. Original QA screenshots are in the report and are separate historical evidence.
`api-timing.json`: 15 API reads, not rendered-page load times.

## Commands and environment

Java 21.0.13 (Temurin, ARM64), local H2, macOS, Vite 5.4.21, in-app Chromium browser.

1. Check out the recorded SHA. Configure JAVA_HOME to Java 21.
2. Run `sh backend/mvnw -B -f backend/pom.xml verify` (the execution used workspace-local Maven download/dependency caches).
3. In frontend, run `npm ci` then `npm run build`.
4. Start services via the supplied runner, then run `python3 api_regression.py` and `python3 followup.py` once on a fresh local database.
5. Browser checks used `http://localhost:5173`; the default gateway allowlist rejects `http://127.0.0.1:5173` as an origin.
6. Browser member checks changed QA Fixture Task 0 to IN_PROGRESS, added then removed member 2 from the committee, RSVP'd GOING to QA Fixture Event 0 and marked all notices read. The restart harness expects these UI actions.
7. Stop all eight Java processes and the frontend, confirm ports are down, restart on the same H2 files, then run `python3 restart_verify.py`.

The fixture credentials in the harness are disposable local test values, not user or production credentials. Do not deploy them. No Supabase or production database was accessed.

## Remaining work and observed defect

At width 390, document scroll widths were 1575px on officer membership and 1258px on member events/tasks. This violates the no-horizontal-scrolling criterion. Officer content edit/delete UI is absent; announcement editing is absent from the API. AI input limits are 3000 in the UI and 5000 in the backend; acceptance criteria need reconciliation.

Do not label the entire broader regression PASS from the successful API assertions. Use the detailed report's scoped results and remaining checks.
