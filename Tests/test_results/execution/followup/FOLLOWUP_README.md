# Follow-up regression on 4a74d05

Executed November 12, 2026 (America/Chicago). Source remained unchanged.

13 additional grouped browser checks passed: officer event create/edit/cancel, valid/alternate/invalid report dates and filtered CSV, committee creation/member assignment/task creation, assignee visibility, 15 page timing samples, expired-session logout, manual AI review/copy/discard, officer announcement/link creation, and past-event end-time classification.

Browser timings were 298–731 ms (five roster refreshes, five event loads, five task loads), including browser-tool command overhead. Dataset at measurement: 20 active regular members plus one officer, one pending applicant, 11 events and 31 tasks. A twelfth short-duration event was later created for the past-boundary check.

A separate same-source user-service/gateway/frontend instance used JWT expiry 20 seconds. API checks returned 200 before expiry and 401 after 21 seconds. Browser navigation after expiry returned to login. This is an accelerated expiry test, not a two-hour idle-wait test. Ports: 8181 user, 8189 gateway, 5175 frontend.

Timeout tests used a controlled delayed HTTP provider (11435), ai-service (8187) and gateway (8180), with unchanged source. Default 30 s returned HTTP 503 at 30.544 s; runtime override 60 s returned 503 at 60.306 s. No 60 s setting was committed. The delayed provider did not generate model output.

Official Ollama v0.40.16 and llama3.2:3b were downloaded. Seven live endpoint attempts (five distinct inputs) failed because the runtime could not create a Metal command queue / allocate its backend. A derived CPU-configured model also failed. These attempts establish an environment blocker, not successful live AI generation or valid cold/warm latency. The raw responses and relevant logs are included; downloaded weights are excluded.

Five-input privacy check: no email addresses, student IDs or fixture passwords in the synthetic inputs; raw notes absent from application service logs. Live H2 ai_request_log column inspection showed only id, input_chars, latency_ms, outcome, requested_at and user_id. Successful generation storage path remains unverified because of the model-loading blocker.

The 390 px member layout overflow was reproduced: sidebar and main content are about 1258 px wide. Existing content edit/delete and task reassignment UI controls are absent; corresponding broad UI criteria cannot be marked passed. No configured Supabase test database was available.

The updated QA report supersedes earlier pending wording. Overall: PARTIAL WITH FAILURES AND BLOCKERS. Follow-up JSON files, screenshots, scripts and logs substantiate the individual results.

All credentials embedded in scripts are synthetic local fixture values. No user/production credentials or private Ollama key are included. Services were stopped after testing; model files remain in the workspace for reproducibility.
