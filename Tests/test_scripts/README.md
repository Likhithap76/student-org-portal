# Test scripts and reproduction

## Application tests

The original JUnit test sources remain at `backend/*/src/test/` to preserve Maven's layout. Run `sh mvnw -B verify` from the backend directory. The recorded 525-test verification belongs to commit 4a74d05; packaging does not rerun it.

## Portable API harness copies

Start the stack with `python3 Tests/test_scripts/start_local.py` from the package root, using a fresh isolated data directory. Ensure no Ollama model service is running at port 11434 for the base suite: one case deliberately expects unavailable-model HTTP 503.

In a second terminal, run once in this order:

```sh
python3 Tests/test_scripts/api_regression.py
python3 Tests/test_scripts/followup.py
```

The first script creates membership, event, attendance, task, content, security and AI-failure fixtures. The second depends on the first script's `fixture.json`, verifies organization-local date reporting, and creates the larger timing dataset. They mutate local data and are not idempotent; do not point them at production. Run both on the same America/Chicago calendar date. Inspect JSON result files under `Tests/test_results/reproduced/`; a failed recorded assertion produces a nonzero exit code.

These copies adapt output paths and correct the original UTC-date and CSV-count assertions. They were syntax-checked for packaging; historical execution counts refer to the unchanged scripts and results under `executed/` and `../test_results/execution/`.

## Restart verification prerequisites

`restart_verify.py` is state-dependent. After the two scripts, use the browser as the fixture member to set `QA Fixture Task 0` to IN_PROGRESS, RSVP GOING to `QA Fixture Event 0`, and mark notifications read. As officer, remove the second member from the fixture committee so only `memberId` in fixture.json remains. Then stop and restart the full backend while preserving its data directory and run:

```sh
python3 Tests/test_scripts/restart_verify.py
```

It also checks the profile/password changes, cancellation, attendance, content and completed task produced by the base harness. See the historical browser evidence for the original sequence.

## Original executed scripts and browser evidence

`executed/original/` and `executed/followup/` preserve the actual scripts used in the recorded runs, including separate timeout/expiry/AI checks. They contain original workspace/tool-runtime paths and are reference scripts, not plug-and-play launchers. Adapt those paths and service ports in an isolated environment before rerunning. Browser checks include recorded manual/tool-driven interactions and screenshots; do not treat all browser observations as a standalone Selenium suite. Unprovided teammate scripts are not invented or claimed to be included.

All embedded credentials are disposable local fixtures. New output must remain separate from historical results.
