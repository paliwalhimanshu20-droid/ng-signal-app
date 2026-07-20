# AUTH-001 — Multi-Device Google Workspace Authentication Architecture
### Integration Specification | Subordinate to the Constitution and JARVIS-001–004
### Status: Draft for Owner Review

*This is not a sixth core document — JARVIS-004 explicitly closed the core series. This is a subordinate integration specification, the same tier as a future JARVIS-00X-Integration-Gateway document would be, scoped to one question: how does Google Workspace authentication (Sprint 13) generalize from "one Android app" to "the owner's whole device fleet" without becoming platform-specific in JARVIS Core.*

---

## 1. Where this starts from

Sprint 13 shipped a real, working OAuth implementation: `GoogleAuthManager` on Android, using AppAuth + Chrome Custom Tabs + PKCE, storing an AppAuth `AuthState` (refresh token included) in `EncryptedSharedPreferences` backed by the Android Keystore. That implementation is correct and stays as-is — this document does not replace it, it generalizes the *shape* around it so a tablet, a desktop app, and a future web client can each get their own correct implementation without JARVIS Core caring which one it's talking to.

Two things are true simultaneously and drive every decision below:

- **The refresh token itself must never leave the device it was issued on.** Android Keystore, macOS Keychain, Windows DPAPI, and a browser's `httpOnly` session cookie are all *deliberately* non-exportable — that's a security boundary, not a gap to engineer around. Any design that tries to "sync the token" across devices is building around a safeguard, not with one.
- **The owner still experiences one JARVIS, not five independently-configured ones.** Connection status, granted scopes, and last-sync time should look the same on every device without the owner re-entering anything they don't have to.

The resolution: **per-device credentials, shared status.** Section 4 is where this gets concrete.

## 2. Per-platform OAuth flow mapping

**Objective.** Pick the Google-supported flow for each platform, not one flow forced onto all of them.

| Platform | Flow | Client type in Google Cloud Console | Where the refresh token lives |
|---|---|---|---|
| Android phone / tablet | AppAuth, Custom Tabs, PKCE, custom-scheme redirect (`com.jarvis.os.app:/oauth2redirect`) | "iOS" type (Sprint 13's documented workaround) | Android Keystore via `EncryptedSharedPreferences` |
| Desktop (future) | Same OAuth family, PKCE, **loopback redirect** (`http://127.0.0.1:PORT`) instead of a custom scheme — Google's own documented flow for installed desktop apps | "Desktop app" type | OS credential store (macOS Keychain / Windows DPAPI / libsecret on Linux) |
| Web (future) | **Authorization Code flow with a confidential backend client** — the browser never sees a refresh token, only a session cookie; the backend holds the refresh token and proxies API calls | "Web application" type, client secret held server-side | Backend's own encrypted store (not the browser) |

**Decision.** Three different Google client registrations, one per platform family, all under the same Google Cloud project (same enabled APIs — Gmail, Calendar, Drive — same consent screen, same fixed scope list from `GoogleOAuthConfig`). Tablet needs no new registration — it's the same Android client type as phone; a tablet running the JARVIS APK authenticates exactly like a phone does today.

**Alternative considered — one universal flow.** Using the Android custom-scheme approach everywhere (including desktop/web) was rejected: Google does not support custom URI-scheme redirects for confidential web clients, and encouraging a desktop app to masquerade as a mobile client is exactly the kind of workaround Sprint 13's own "iOS-type client on Android" already stretches once; stacking a second stretch on top of it for desktop would leave both flows fragile to a future Google policy tightening. Better to use the flow each platform actually has first-class support for.

**Consequence.** JARVIS Core, and everything above `AuthenticationProvider`, never needs to know any of this table exists — see Section 3.

## 3. The `AuthenticationProvider` abstraction

**Objective.** One contract every platform's Google implementation satisfies, so a status provider (Gmail/Calendar/Drive today; Maps/Photos/Tasks/Contacts tomorrow) never imports a platform-specific class.

**Decision.** `AuthenticationProvider` is extracted as the base interface `GoogleAuthManager` now implements:

```kotlin
interface AuthenticationProvider {
    fun isConnected(): Boolean
    fun connectionHealth(): GoogleConnectionHealth
    fun grantedScopes(): Set<String>
    suspend fun getFreshAccessToken(): Result<String>
    fun disconnectLocally()
    suspend fun revoke(): Result<Unit>
}

interface GoogleAuthManager : AuthenticationProvider {
    fun buildAuthorizationIntent(): Intent          // Android-specific launch surface
    suspend fun handleAuthorizationResponse(intent: Intent): Result<Unit>
}
```

Everything that isn't the initial "kick off a sign-in" step (which is unavoidably platform-shaped — an `Intent` on Android, a `Process` launch + local HTTP listener on desktop, a redirect response on web) lives on `AuthenticationProvider`. `GoogleWorkspaceStatusProvider` and every future connector (`GoogleMapsStatusProvider`, etc.) depend only on `AuthenticationProvider`, never on `GoogleAuthManager` directly — the same "depend on the interface, not the Mock/Real implementation" seam this codebase already uses everywhere (`ConnectionRepository`, `ToolRepository`, every `*ChatProvider`).

**Alternative considered.** A single fat interface with all three platforms' authorization methods (`buildAndroidIntent()`, `buildDesktopLoopbackListener()`, `handleWebRedirect()`) implemented as no-ops on the platforms where they don't apply. Rejected: violates the "sharp seams" principle from JARVIS-001 Section 3 — a Desktop `AuthenticationProvider` shouldn't compile with an unused `buildAuthorizationIntent(): Intent` method it can never call. The split above means each platform module implements only what it needs.

**Consequence.** Adding Maps/Photos/Tasks/Contacts later is "add scopes to `GoogleOAuthConfig`, add a new `*StatusProvider` that takes an `AuthenticationProvider` in its constructor" — zero changes to authentication code, on any platform. This directly satisfies the requirement that future Google services integrate "without architectural changes."

## 4. Shared status, not shared secrets

**Objective.** Make "connection status, granted permissions, and sync metadata... consistent across devices" true without moving a refresh token off the device that owns it.

**Decision.** Two layers, cleanly separated:

- **Layer 1 — Credential layer (per device, never synced).** Each device's `AuthenticationProvider` implementation independently completes its own OAuth flow and holds its own refresh token in its own OS-level secure store. A phone being lost and wiped invalidates only that phone's credential — desktop and tablet are unaffected. This is the same failure-isolation property a physical door key gives you: losing one doesn't require re-keying every door.
- **Layer 2 — Status layer (metadata only, synced through a backend).** *Connected: yes/no, granted scope names, last-sync timestamp, last-token-refresh timestamp, connection health* — none of that is a secret, all of it is small, and all of it is exactly what today's Sprint 13 `GoogleWorkspaceTokenStore.currentConnectionInfo()` already computes locally. A future lightweight sync endpoint (part of the still-unbuilt backend bridge JARVIS-001 and `ConnectionRepository`'s own docstring already flag as pending) lets each device push its own status snapshot and read the others' — so the tablet can honestly say "phone last synced 4 minutes ago" without ever touching the phone's token.

**Explicitly rejected alternative — sync the refresh token itself** (e.g., via a shared encrypted blob in Drive, or a custom sync service). This was the most tempting shortcut to "seamless multi-device" and the one this document spends the most words rejecting: an exported, syncable refresh token is a bearer credential that, once it leaves Keystore/Keychain, can be replayed from anywhere — turning one compromised sync channel into "attacker has Gmail/Calendar/Drive read access from any device," which is a strictly worse security posture than today's Android-only, Keystore-only implementation. **No requirement in this brief is worth that trade**, and "seamless" is satisfied by Layer 2 without it.

**Honest gap.** Layer 2 needs a backend JARVIS does not have yet (confirmed absent by `ConnectionRepository`'s own "BACKEND STATUS" docstring). Until it exists, each device's connection status is real and locally correct but not cross-visible — a tablet genuinely cannot know the phone is connected. This document specifies the shape Layer 2 will take; it does not fake it into existing early. See Section 6.

## 5. Device registration and Owner Sovereignty

**Objective.** "Every new device... must require explicit owner approval and be fully auditable" — without inventing new governance machinery this codebase doesn't already have.

**Decision.** Reuse, don't reinvent: `ApprovalRepository`'s existing `ApprovalKind.CONNECTION_REQUEST` already models "something is asking to connect and needs a yes/no from the Owner, on the record." A new device completing its *first* Google sign-in raises a `CONNECTION_REQUEST` approval (provider id = a stable per-device identifier, e.g. `"google-workspace:{device-label}"`) exactly the way approving a brand-new integration does today — same state machine (`PENDING → APPROVED/REJECTED`), same append-only `ApprovalAuditRecord` trail, same UI pattern already built in `ConnectionsScreen`. A *second* sign-in on a device that already has an approved, non-revoked connection record does not re-prompt — that would be the "repeated manual setup" this brief explicitly asks to avoid, and it isn't what "new device" governance is protecting against.

**Alternative considered.** A dedicated `DEVICE_AUTHORIZATION` `ApprovalKind` enum case. Rejected for now: `ApprovalKind` is a Kotlin enum switched over exhaustively in several places already in this codebase; adding a case is a real, mechanical, low-risk change, but it buys nothing `CONNECTION_REQUEST` doesn't already give a device-scoped connection id — reusing it keeps this change to zero touched `when` blocks elsewhere. Revisit only if device approvals need a materially different UI treatment than connection approvals do.

**Scope-expansion rule carries over unchanged.** `GoogleOAuthConfig.SCOPES` from Sprint 13 remains the single source of truth for every platform's authorization request — a desktop or web implementation requesting a scope not in that list is a bug, not a feature; the fixed-list, code-review-is-the-approval principle from Sprint 13 is platform-agnostic by construction, not something each platform re-implements.

## 6. What's real today vs. what this specifies for later

| Claim | Status |
|---|---|
| Android phone: full native OAuth, refresh, revoke | **Built** (Sprint 13) |
| Android tablet | **Built, today, with zero additional code** — same APK, same `AuthenticationProvider` implementation; a tablet is not a new platform to this architecture, it's the same Android client type running on different hardware |
| `AuthenticationProvider` interface extraction | **Delivered with this document** (Section 3) — non-breaking, `GoogleAuthManager`'s existing methods are unchanged, just re-homed under a base interface |
| Desktop OAuth (loopback PKCE) | Specified (Section 2), not built — no JARVIS desktop app exists yet to build it into |
| Web OAuth (backend-mediated) | Specified (Section 2), not built — blocked on the backend bridge that doesn't exist yet, same dependency `ConnectionRepository` has always flagged |
| Cross-device status sync (Layer 2) | Specified (Section 4), not built — same backend dependency |
| Device-scoped approval via `CONNECTION_REQUEST` | Specified (Section 5), not built — depends on a device-identity concept that doesn't exist in the Android app yet either |

Nothing in the "not built" column is faked into looking built — consistent with this codebase's standing rule against reporting fabricated success. Building each row is future-sprint work, gated on the platforms/backend it needs actually existing first.
