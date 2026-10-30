# ai-service — notes for the AI module

The Spring Boot side is done: endpoint, security, request log, assignee matching and tests.
Your job is to plug a real LLM in behind **one interface**.

## How it works
`POST /api/ai/summaries` (OFFICER_ADMIN) with `{"text": "...", "committeeId": 5}` returns
`{summary, decisions[], actionItems[{description, assigneeName, suggestedUserId, dueDate}]}`.

Until the real client is built, the endpoint returns **503** ("The AI service is unavailable").

## What you implement
Fill in `summarize(notes)` in **`client/HttpLlmClient.java`** (the template with the `TODO`). The backend only needs:
- **Return** a `MeetingSummary`: summary, decisions, and action items (description, assigneeName, dueDate).
- **Throw** `LlmUnavailableException` on timeout or provider error (API returns 503), and
  `LlmInvalidOutputException` when the answer can't be used (API returns 502).
- **Settings** are already wired: `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` in `backend/.env`
  (`orgportal.ai.*` in `application.yml`).

The prompt, validation, PII handling and evaluation are your choice.
Update `HttpLlmClientTest` (it currently expects the "not configured" error).

## Already added
- Officer-only access, input length check (1–5,000 chars → 422), error responses.
- Matching assignee names to committee members (`suggestedUserId`).
- `ai_request_log` row per call with outcome and latency — never the text or the answer.
- Creating tasks: the frontend sends accepted items to `POST /api/tasks/batch`.

**Never log the notes or the model's answer.**
