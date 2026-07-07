# NGSP-004: Instrument Master sync should fail loudly on empty/invalid DB, not just missing DB

**Status:** Backlog
**Owning Agent:** QA & Validation
**Opened:** 2026-07-07
**Related ADR (if any):** —

## Request
`update_instrument_master.yml`'s commit step currently only checks whether
`data/instrument_master.db` exists before committing:

```yaml
if [ -f data/instrument_master.db ]; then
  git add data/instrument_master.db ...
else
  echo "data/instrument_master.db was not created — sync must have failed."
  exit 1
fi
```

This catches a fully missing file, but not a file that exists yet is empty,
truncated, or contains zero/near-zero instrument rows (e.g. `run_update.py`
crashes partway through a write, or Upstox returns a malformed/partial
response that still produces a valid-but-nearly-empty SQLite file). In that
case the workflow would happily `git add` and commit a bad DB over the
last known-good one, silently degrading the instrument universe the
Streamlit app and NGWH-002 downloader both depend on.

## Chief Architect Assessment
Fits roadmap: Yes — this is a hardening fix to an existing automated
pipeline (NGSP-003A.1), not a new module. No schema or architecture change.
Modules affected: `.github/workflows/update_instrument_master.yml`,
`instrument_master/validation.py` (may already have a reusable row-count
or integrity check to call here — check before writing a new one).
Notes: Low risk, should not block other work.

## Assigned To
QA & Validation Agent (define the check + threshold) → Data Engineering
Agent (implement in workflow/validation code) → QA & Validation Agent
(verify) → Documentation Agent (update ARCHITECTURE.md / SOP if the sync
contract changes).

## Work Log
-

## Blockers
-

## Proposed Acceptance Criteria
- Workflow step (or a script it calls) verifies `data/instrument_master.db`
  has a row count above a sane minimum threshold (e.g. compare against the
  previous commit's count, or a fixed floor derived from the known ~126,644
  instrument universe) before allowing the commit.
- If the check fails, the workflow exits non-zero **and does not commit**,
  preserving the last known-good `instrument_master.db`.
- Failure is visible without digging into Actions logs — reuse the existing
  validation report artifact upload pattern, or add a Telegram/email alert
  consistent with how `check_signals.yml` already alerts.
- Existing FAIL-severity validation rules in `instrument_master/validation.py`
  are checked first — this ticket should not duplicate logic that already
  exists there.

## QA & Validation Result
Pass / Fail / Not yet tested
Notes:

## PR Link


## Closed
Date:
Outcome summary:
Logged in agent_log.md: Yes / No
