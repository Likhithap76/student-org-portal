# Broader regression: recorded scope

Commit: `4a74d05f89b1c4d837456cc06af4d85bd49c1045`. Authoritative detailed results: `../test_results/QA_Report_4a74d05.pdf`. Evidence is under `../test_results/execution/`.

| Area | Procedure / expected behavior | Recorded disposition |
|---|---|---|
| Membership and attendance | Approve/reject applicants; authorize roles; record attendance; reject duplicate effects; reload | Tested API/browser paths passed |
| Events and RSVP | Officer creates, edits and cancels events; member RSVPs; verify upcoming/cancelled/past grouping | Tested paths passed |
| Committees and tasks | Create committee, add/remove member, assign task, view as assignee, change status | Tested paths passed; task reassignment UI absent |
| Reports and CSV | Compare attendance/completion totals; use valid, alternate and reversed dates; compare filtered CSV | Follow-up passed; two initial UTC-date harness errors retained as history |
| Profile and security | Change profile/password, reject old password, promote/demote/deactivate/reactivate, enforce roles and token expiry | Tested paths passed; expiry accelerated to 20 seconds |
| Content and notifications | Publish announcement/link; check member visibility and notification read state | Tested paths passed; edit/delete UI coverage incomplete |
| AI | Reject empty/oversized input; unavailable provider; controlled timeout; live model attempts; review/copy/discard; log/privacy inspection | Failure handling and manual review passed; live model generation blocked; successful-generation privacy path unverified |
| Reliability and responsiveness | Restart all services preserving H2; check persisted state; 15 page timings; mobile width 390 px | Restart and sampled timing passed; mobile horizontal overflow failed |
| Deployment database | Repeat integration against configured Supabase test database | Not executed: test database unavailable |

The follow-up adds 13 grouped browser checks, including event lifecycle, report date/CSV behavior, committee/task assignment, page timing, expiry, manual summary review and content publishing. A grouped check may contain multiple interactions. The recorded 182 successful API assertions include setup operations and are not independent acceptance-case counts. No unresolved issue is converted to PASS by packaging.
