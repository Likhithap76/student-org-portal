# AI Meeting Summarization — Implementation and Evaluation

## Implementation
- Frontend: React and TypeScript
- Backend: Java Spring Boot AI service
- Model: Local Ollama llama3.2:3b
- API: POST /api/ai/summaries
- Input field: text
- Output: Summary, decisions, suggested action items
- Access: Officer-facing UI
- Review: Edit summary, decisions and tasks; remove suggestions; copy results
- Limitation: Suggested tasks are not automatically saved to the database.

## Five Synthetic Evaluation Cases

| ID | Result | Observations |
|---|---|---|
| AI-01 Volunteer fair | Partial | Correct event facts and owners; year-free deadlines absent from structured date fields |
| AI-02 Membership review | Partial | Correct application counts; omitted no-approval-decision detail and inferred tasks |
| AI-03 Committee status | Partial | Omitted in-progress status and pending invitation; inferred tasks |
| AI-04 Budget meeting | Partial | Correctly reported no purchases; omitted $120 available budget |
| AI-05 Event planning | Partial | Correct cancellation, proposed event and unconfirmed venue; general guidance classified as task |

All five cases returned usable output.
All five required human review or correction.
No fully accurate result is claimed for these cases.

## Negative UI Tests

| ID | Scenario | Result |
|---|---|---|
| N-01 | Empty meeting notes | PASS — Generate button disabled |
| N-02 | Input exceeding 3000 characters | PASS — Input limited to 3000 characters |
| N-03 | Browser network set to Offline | PASS — Readable error; no fabricated response |

N-03 tests network failure, not a direct Ollama-provider outage.

## Human Review Tests

- Generated meeting summary successfully.
- Edited a summary to restore an omitted decision.
- Copied the edited result successfully.
- Removed inaccurate suggested action items.
- Copied results without removed tasks.
- Corrected display of missing assignees to Unassigned.

## Limitations

- Small models may omit important facts or infer unsupported tasks.
- Dates without an explicit year require human review.
- Officers must review AI-generated output before using it.
- The feature does not automatically create database task records.
- Backend authorization testing and broader Selenium regression
  are tracked separately by the QA/integration team.

## Evidence
Screenshots: AI-01 through AI-05, N-01 through N-03,
AI-DEMO-01-successful-summary.png, and human-review evidence.

## Contributor
Rhea Malpekar — AI integration, React interface,
evaluation cases, human-review controls and evidence.
