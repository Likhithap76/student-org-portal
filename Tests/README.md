# Jindagi Patel QA contribution

Tested application commit: `4a74d05f89b1c4d837456cc06af4d85bd49c1045`.

Broader regression was executed. Overall status: **PARTIAL WITH FAILURES AND BLOCKERS**. Read `test_results/QA_Report_4a74d05.pdf` for scope, results and limitations. Historical results are preserved in `test_results/execution/`; adapted harnesses and reproduction instructions are in `test_scripts/README.md`.

The application source lives at repository-root `backend/` and `frontend/`. Build the backend with Java 21 and `sh mvnw -B verify`, then run `python3 Tests/test_scripts/start_local.py` from the repository root. Use synthetic fixtures only. Original executed scripts retain their historical workspace paths.

This contribution commit adds evidence and documentation; the tested source SHA above is intentionally the earlier application commit.
