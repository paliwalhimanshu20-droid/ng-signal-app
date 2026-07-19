# Sprint 15 — Google Workspace Executive Intelligence Completion: Report

## Phase 1 — Full Architecture Review: the real execution path

Traced directly from code, not assumed. For "Can you check my calendar?":

```
ChatScreen (user types, taps send)
  -> ChatViewModel (collects JarvisCore.events, calls JarvisCore.sendChatMessage(text))
    -> JarvisCore.sendChatMessage(text)
         1. publish(CoreEvent.ChatMessageSent)
         2. decisionEngine.decide(text)              -- JarvisDecisionEngine: needsBriefing? needsOrchestration? (both false here)
         3. intentRouter.classify(text)               -- IntentRouter: iterates ToolRepository.discover(),
                                                           finds GoogleCalendarTool's triggerKeywords contains "calendar"
                                                           -> IntentClassification(toolId = "google_calendar")
         4. buildToolBackedContextHint(text, decision, classification)
              -> runTool("google_calendar", text)
                   -> ToolRepository.execute("google_calendar", text)   -- RiskLevel.LOW, runs immediately, no approval
                        -> GoogleCalendarTool.execute(text)
                             -> GoogleWorkspaceStatusProvider.getTodaysEvents()
                                  -> AuthenticationProvider.getFreshAccessToken()   -- silent refresh if needed
                                  -> ONE Calendar API call (not Gmail, not Drive)
                             -> CalendarFetchResult.Success(events) or .Failure(message)
                   -> publish(CoreEvent.ToolExecuted)   -- audited, same as a Tools-screen-triggered run
              -> contextHint = "<real event list or honest failure>. Answer naturally, don't say you lack access."
         5. chat.sendMessage(text, contextHint)
              -> promptForProvider = contextHint + "\n\n" + text
              -> router.active.sendMessage(sessionId, promptForProvider)   -- e.g. GroqChatProvider
              -> ChatChunk stream (Token/Complete/Error) collected, written into ChatRepository.messages
         6. publish(CoreEvent.ChatResponseReceived)
    <- ChatViewModel observes ChatRepository.messages via StateFlow, renders the new bubble
  <- ChatScreen recomposes
```

Every step above is a real, traced code path (file:function), not inference. The two files that decide "does a tool run at all" are `IntentRouter.kt` and `JarvisCore.sendChatMessage` — nothing else in this list makes that decision.

## Phase 2 — Root Cause Analysis

**No screenshot was attached to this sprint's brief**, so Phase 2's literal instruction ("determine why the attached screenshot...") has nothing to analyze against. What I can state with certainty instead, checked directly against the live repository before writing any of this sprint's code:

**The Intent Router built last session was never uploaded.** `IntentRouter.kt` did not exist in the live repo, `JarvisCore.kt` still had the pre-Intent-Router version, and `ConnectorTools.kt`/`RepositoryModule.kt` were still the pre-Intent-Router versions. If the generic-LLM-response screenshot from two sessions ago were retested against the *live* app today, it would still fail — not because of a new bug, but because the fix that addresses it was still sitting in an unshipped zip. This is now folded into this delivery so it can't happen again.

Going through Phase 2's own list of possible causes for completeness, against the code as it existed before this sprint's Intent Router shipped at all:
- **IntentRouter never executed** — true, but only because it didn't exist yet in the running app.
- Tool never executed / returned empty / output discarded / context never injected / LLM fallback replaced it / UI rendered wrong response / exception swallowed — **none of these apply**; the code trace in Phase 1 shows no place any of these could happen once the Intent Router *is* deployed. `buildToolBackedContextHint` always either gets a real `ToolResult.Success` or `.Failure` and always includes one of the two in the context hint actually sent to the provider.

## Phase 3 — Duplicate Intent Classification: removed

**Before:** `IntentRouter` carried a `ChatIntent` enum (CALENDAR/GMAIL/DRIVE/...) chosen via a hardcoded keyword table, but `JarvisCore` only ever read `.toolId` off the result — the enum value was computed and never used for anything. Separately, the old monolithic `GoogleWorkspaceTool` re-ran its *own* near-identical keyword check on the same raw text to decide which of Calendar/Gmail/Drive to answer. Same message, classified twice, by two different pieces of code that could in principle disagree.

**After:** `IntentClassification` no longer carries an intent enum at all — just `toolId` (what actually gets used) and `matchedKeyword` (diagnostic only). Routing keywords live on `ToolDefinition.triggerKeywords`, owned by each tool. `IntentRouter` is now ~15 lines: iterate registered tools, return the first keyword match. One classification, one place, done once per message.

## Phase 4 — Google Workspace split into four capabilities

`GoogleWorkspaceTool` (one class, `toolId="google_workspace_status"`) is gone, replaced by:

| Tool | toolId | Provider call |
|---|---|---|
| `GoogleCalendarTool` | `google_calendar` | `getTodaysEvents()` |
| `GoogleGmailTool` | `google_gmail` | `getUnreadEmails()` |
| `GoogleDriveTool` | `google_drive` | `getRecentDriveFiles()` |
| `GoogleWorkspaceHealthTool` | `google_workspace_health` | `refreshAll()` |

All four share `RealGoogleWorkspaceStatusProvider` (OAuth, token store, HTTP client are genuinely one thing underneath — no reason to duplicate that), but each is a distinct, independently chat-routable capability with its own `triggerKeywords`.

## Phase 5 — Provider calls optimized

`GoogleWorkspaceStatusProvider.refresh()` (renamed `refreshAll()`) used to be the *only* way to get any Google data, and it always fetched Gmail + Calendar + Drive together — three network calls minimum for a single-capability question. Split into:

- `getTodaysEvents()` — Calendar API only
- `getUnreadEmails()` — Gmail API only (two calls: important-unread list + total unread count)
- `getRecentDriveFiles()` — Drive API only
- `refreshAll()` — still fetches all three, kept for the Settings screen's combined health card, which legitimately needs the full picture regardless of what was last asked

"Can you check my calendar?" now makes exactly one Google API call, not three.

## Phase 6 — Executive Tool Orchestration

This is already the shape Phase 1's trace shows: Intent Router picks a tool (or doesn't) *before* any LLM call happens, `runTool` executes it, and only the LLM's *phrasing* of an already-known result is generated by the model — the LLM never decides whether a connector exists or gets asked to guess at data it doesn't have. This was true architecturally before this sprint; Phases 3–5 made the routing step correct and non-duplicated, they didn't change the overall shape.

## Phase 7 — Executive Response Behaviour

`buildToolBackedContextHint`'s two branches are the actual mechanism this phase asks for:
- Success: *"Real, current data for this: \<result>. Answer naturally using this data — do not say you lack the ability to check this, you just did."*
- Failure: *"Attempted to check this just now but it failed: \<message>. Tell the owner honestly what happened — do not claim you have no ability to check this at all, only that this specific attempt failed."*

**Honest limit, stated plainly:** this is a strong instruction to the model, not a hard guarantee — no prompt-level instruction can make a third-party LLM provably never say a stray disclaimer. What *is* guaranteed by the code path itself: the real tool result reaches the prompt every time a tool is selected, so the model is never left to fall back on "I don't have access" out of actual ignorance — only an unlikely model-level slip could produce it now, not a missing-context bug.

## Phase 8 — Multi-Connector Architecture

Verified concretely, not just designed: adding a hypothetical `WeatherTool` today requires **zero changes** to `IntentRouter.kt` or `JarvisCore.kt` — write the `Tool` implementation with its own `triggerKeywords`, add one `@Binds` line in `ToolModule.kt`. That's the entire integration surface, confirmed by reading `IntentRouter.classify()`'s implementation, which has no per-connector code at all.

## Phase 9 — Screenshot Review / UX Recommendations

**Not implemented this sprint** — deliberately. Phase 9 asks for real UI/UX work (avatar prominence, loading indicators mid-tool-execution, calendar/email response cards, connected-system indicators). Attempting that at the same time as the architecture rework in Phases 3–8 risked shipping both halves worse-verified than either alone. Recommendations, separated out as requested:

- **Tool-execution loading state**: there's no `CoreEvent` today published *before* a tool runs (only `ToolExecuted`, after). Adding a `ToolStarted` event would let `ChatScreen` show "Checking your calendar…" — real value, but touches four other files with exhaustive `when (event)` blocks (`ChatScreen`, `HomeViewModel`, `NotificationFactory`, `AuditFactory`), so it's sized as its own follow-up rather than folded in here.
- **Structured response cards** for calendar/email results (event list as a compact card instead of a text paragraph) would read as more "executive assistant," less "chatbot" — worth a dedicated design pass, not a quick addition.
- **Connected-system indicator** in the chat header (small dot/icon showing GitHub/Google/NG Signal Pro connection health) would give ambient awareness without opening Settings.

## Phase 10 — Validation Matrix

Traced through the actual code for each scenario (not live-tested — this sandbox has no way to run the Android app or hit real Google/GitHub APIs). "Intent selected" below is really "toolId selected," per Phase 3's fix.

| Scenario | Tool selected | API(s) called | Context given to LLM | Notes |
|---|---|---|---|---|
| "Check my calendar" | `google_calendar` | Calendar only | Real event list or failure | |
| "What's on today's agenda?" | `google_calendar` | Calendar only | Real event list | matches "agenda" keyword |
| "Next meeting" | `google_calendar` | Calendar only | First event or "no meetings left" | dedicated branch in tool |
| "Am I free this afternoon?" | `google_calendar` | Calendar only | Event count + list, or "free all day" | approximate — doesn't parse "afternoon" specifically, see below |
| "Any unread emails?" | `google_gmail` | Gmail only | Unread count + important list | |
| "Important emails" | `google_gmail` | Gmail only | Same as above | |
| "Email summary" | — none | — | — | **gap found, see Phase 11** |
| "Recent files" | — none | — | — | **gap found, see Phase 11** |
| "Search Drive" | `google_drive` | Drive only | Recent files list | matches "search drive"; not a real search, just recent files — honest limit, tool doesn't claim to search |
| "Is Google connected?" | `google_workspace_health` | All three (by design — health check) | Combined snapshot or failure | |
| "Reconnect" | `google_workspace_health` | All three | Honest failure ("reconnect under Settings") if disconnected | tool cannot itself launch OAuth — correct, that needs user interaction |
| Expired token | any Google tool | attempted, fails at token step | Honest "reconnect" failure message | `AuthenticationProvider.getFreshAccessToken()` surfaces this before any API call |
| Network unavailable | any Google tool | attempted, fails | Honest network-failure message | caught in each provider method's try/catch |

## Phase 11 — Architecture Audit: additional issues found

- **"Email summary" and "recent files" (bare) don't route.** `google_gmail`'s keywords require "gmail"/"my email"/"my inbox"/"unread mail"/"unread email"/"important email" — a bare "email summary" doesn't hit any of them, same gap for "recent files" against `google_drive`'s keywords. Not fixed in this pass (keyword tuning is inherently iterative and low-risk to adjust later); flagged here rather than silently left for someone to rediscover.
- **"Free this afternoon" is answered approximately.** The tool doesn't parse "afternoon" as a time window — it reports the full day's event count. Correct in spirit (never fabricates a specific answer it can't verify), imprecise in practice.
- **The old `ChatIntent` enum was genuinely dead code** — Phase 3 already covers the fix, listed here as the audit finding that justified it: a computed-but-never-read value is exactly the kind of duplicated responsibility Phase 11 asks to surface.

## Phase 12 — Deliverables

1. **Root cause analysis** — Phase 2 above.
2. **Updated architecture** — Phase 1's trace, now accurate to the code in this delivery.
3. **Code implementation** — 8 files, listed in the delivery message.
4. **Validation report** — Phase 10 above (code-traced, not live-tested — no environment here to run the app against real Google/GitHub APIs).
5. **Screenshot review** — Phase 9 above; no screenshot was attached to review this round.
6. **Performance improvements** — Phase 5: calendar-only questions now make 1 API call instead of 3.
7. **Additional issues discovered** — Phase 11 above.
8. **Recommended future enhancements** (explicitly separate from completed work) — Phase 9's UI recommendations, plus the two keyword gaps in Phase 11.

---

# Executive Integration Audit (pre-upload, final pass)

Requested before this sprint's upload, covering five specific items against the code as it stands after Phases 1–12 above. Each item below was verified against the real code, not assumed — findings led to two real code changes (multi-tool execution, tool-execution feedback), not just documentation.

## 1. Multi-tool Requests — was NOT supported, now is

**Verified:** before this audit, `IntentRouter.classify()` returned only the *first* matching tool (`firstOrNull` in a loop with early return). For "Do I have meetings today and any important unread emails?", only `google_calendar` would have run — `google_gmail` never executed, and nothing told the model or the owner that half the question went unanswered. This matches this audit's own concern exactly.

**Fixed, not just documented:** `IntentRouter.classify()` → `classifyAll()`, returning every matching tool instead of the first. `JarvisCore.buildToolBackedContextHint` now loops over every matched `toolId`, runs each one (each still gets its own `ToolStarted`/`ToolExecuted` pair and audit entry — nothing about multi-tool execution skips the existing audit trail), and folds every real result into one context hint. A trailing instruction — *"If the owner asked about something none of the above covers, say so honestly rather than guessing"* — covers the remaining case Item 1 also implicitly asks about: a compound question where only *some* parts match a registered tool.

Also widened `GoogleCalendarTool`'s keywords to include "meetings"/"meetings today" — the audit's own first example ("Do I have meetings today...") didn't actually match the prior keyword set (`"my meetings"` required the literal word "my"), which would have made multi-tool execution invisible in the exact scenario meant to demonstrate it.

**Second example verified:** "Check my calendar and show recent Drive files" — `"calendar"` matches `google_calendar`, `"drive files"` matches `google_drive`'s existing keyword set. Both run.

## 2. Tool Execution Feedback — built, not deferred

Sprint 15's original delivery deferred this into Phase 9 (UI recommendations) on the assumption it required touching 4 files with exhaustive `CoreEvent` handling. Re-checked precisely for this audit: only **2** files actually have exhaustive `when` blocks over `CoreEvent` (`NotificationFactory.kt`, `AuditFactory.kt`) — `ChatScreen.kt`'s event collector uses a guard-condition `when { }`, not a subject `when(event)`, so it was never exhaustiveness-checked, and `HomeViewModel.kt` doesn't reference `CoreEvent` at all (an earlier grep pass had a false-positive match against an unrelated `VoiceRecognitionEvent` block). With the real number at 2, not 4, this was cheap enough to build for real:

- New `CoreEvent.ToolStarted(toolId, toolName)`, published by `JarvisCore.runTool` *before* `ToolRepository.execute` runs.
- `ChatViewModel` now exposes `workingOnLabel: StateFlow<String?>`, set on `ToolStarted` with hand-tuned phrasing matching this audit's own examples exactly ("Checking your calendar…", "Reading your Gmail…", "Searching Drive…"), falling back to `"Checking $toolName…"` for any connector without a hand-tuned line yet — a new connector is never left with zero feedback.
- `TypingIndicator` (the existing "JARVIS is thinking…" bubble) now takes that label instead of always showing the generic phrase.
- Cleared alongside `isTyping` on `ChatResponseReceived`.

For a multi-tool turn, the label updates to whichever tool started most recently, since they run sequentially — an honest reflection of what's actually happening, not a fabricated "doing everything at once" claim.

## 3. Error Handling — verified, no generic disclaimers found

Checked every failure message actually in the code path a tool failure travels through:

- `AuthenticationProvider.getFreshAccessToken()` failures: *"Google Workspace isn't connected yet. Connect it under Settings, Google Workspace."*, *"Google Workspace needs to be reconnected."*
- HTTP-level failures in `GoogleWorkspaceStatusProvider.getJson()`: 401 → *"Google rejected that access token -- it has likely expired or been revoked. Reconnect Google Workspace under Settings."*; 403 → *"Google denied that request -- check the granted permissions under Settings, Google Workspace."*
- Network/other exceptions: *"Couldn't reach Google Calendar/Gmail/Drive. Check your connection and try again."* (per-capability, not a generic "Google Workspace" catch-all)

Every one of these reaches `ToolResult.Failure(message)` unchanged, which `buildToolBackedContextHint` folds into the context hint as *"Attempted to check this just now but it failed: \<message>."* with an explicit instruction not to claim a permanent capability gap. **No code path in this connector's failure handling can produce a generic "I'm a large language model..." disclaimer** — that phrasing only ever came from the LLM having zero context at all (the original bug, fixed in Sprint 14). The one honest caveat, repeated from Phase 7: this is a strong instruction to the model, not a hard guarantee against every possible model-level phrasing choice.

## 4. Regression Testing

Traced through the code (same method as Phase 10 — no live environment to run the app against real APIs):

- **Calendar/Gmail/Drive/Workspace Health**: unchanged individually; Phase 10's matrix still holds per-tool, now with multi-tool as an additional supported case (Item 1 above).
- **General Chat**: unaffected by construction — `classifyAll()` returning an empty list (no keyword match) routes to the exact same `buildConversationalContextHint` path as before Sprint 14 ever existed; nothing in this audit's changes touches that branch.
- **Existing unit tests**: `JarvisCoreNotificationTest.kt`/`JarvisCoreApprovalTest.kt` construct `JarvisCore` with a `MockToolRepository(emptySet(), ...)` — zero registered tools, so `classifyAll()` always returns an empty list in those tests regardless of message text, meaning this audit's routing changes are inert in both test files' scenarios (Notification/Approval flows, not chat routing) — verified they still compile with the renamed `KeywordIntentRouter` constructor call.

## 5. Executive Experience Review

Honest assessment, not a sales pitch: for the seven capabilities that exist today (Calendar, Gmail, Drive, Workspace Health, GitHub, NG Signal Pro, Streamlit), the interaction now genuinely matches "executive coordinator" rather than "chatbot" — a real tool runs, the owner sees live feedback while it does, a compound question gets a compound real answer, and a failure is reported as a specific, actionable reason rather than a shrug. That's a real, verified change from where this sprint started.

What's still honestly missing from a "true JARVIS" feel, named rather than glossed over: no structured response cards (a calendar answer is still a text paragraph, not a visual event list); no ambient connected-systems indicator in the chat header; and the LLM still exercises its own final say on exact phrasing, per Phase 7's caveat. All three are named in Phase 9 as scoped future work, not silently left as a gap someone has to rediscover.

---

# Post-Audit Follow-up: Vocabulary Expansion + Feedback Confirmation

## Item 1 — Routing vocabulary expanded across every connector

Went through all seven tools' `triggerKeywords` and added natural-language synonyms, not just the two explicitly named gaps. Counts before -> after:

| Tool | Before | After | Notable additions |
|---|---|---|---|
| `github_status` | 6 | 17 | "pull requests", "open pr", "ci status", "build status", "github actions", "repo status", "recent commits", "what changed", "my issues", "open issues" |
| `ng_signal_pro_status` | 6 | 12 | "scanner status", "scanner running", "warehouse status", "warehouse updated", "any trades", "trading signals" |
| `streamlit_status` | 4 | 10 | "deployment status", "deployment problems", "dashboard status", "app healthy", "open ng signal pro", "open the dashboard" (this last one is literally the exact executive question from Sprint 13's own brief -- it was never actually wired to route anywhere until now) |
| `google_calendar` | 10 | 15 | "any meetings", "my schedule", "today's schedule", "upcoming events", "am i busy" |
| `google_gmail` | 6 | 12 | "email summary", "summarize my email(s)" (the two named gaps), plus "inbox summary", "new emails", "any emails" |
| `google_drive` | 5 | 9 | "recent files", "latest files" (the two named gaps), plus "latest documents", "my files" |
| `google_workspace_health` | 5 | 9 | "google connected", "workspace connected", "workspace status", "workspace healthy" |

Verified no cross-tool ambiguity: wrote a script to check every keyword across all seven tools for exact duplicates -- zero found. Each keyword is specific enough to its own domain noun (calendar/email/drive/repo/scanner/dashboard) that ordinary unrelated messages shouldn't spuriously match, consistent with the low-stakes/read-only reasoning that already justified auto-running these tools in the first place.

One gap found during this pass, fixed as low-risk: Sprint 13's original brief listed "Open NG Signal Pro." as one of Streamlit's own named executive questions -- it had never actually been added as a trigger phrase for `streamlit_status` until this review. Added ("open ng signal pro", "open the dashboard").

## Item 2 — Tool execution feedback: already complete

Checked before building anything new -- `CoreEvent.ToolStarted`, `ChatViewModel.workingOnLabel`, and the `TypingIndicator` label wiring were all built in the previous Executive Integration Audit round. One small wording fix applied: "Searching Drive..." -> "Searching Google Drive..." to match this follow-up's exact phrasing. No other changes needed -- re-verified end to end (`runTool` publishes `ToolStarted` before execution, `ChatViewModel` sets the label, `TypingIndicator` renders it, both clear on `ChatResponseReceived`).

