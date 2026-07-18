# AUTH-002 — Device Management Center
### Integration Specification | Subordinate to AUTH-001 and the Constitution
### Status: Draft for Owner Review — **blocked on the backend bridge; not buildable yet**

*Builds directly on AUTH-001's Layer 1/Layer 2 split (per-device credentials, shared metadata). Where AUTH-001 specified that split in the abstract, this document specifies the one screen and the one backend surface that actually uses it: the place the Owner goes to see and manage every device JARVIS runs on.*

---

## 1. Why this is a spec, not code, today

Every requirement in this brief — "display every authorized device," "disconnect a device," "revoke only one device without affecting others" — presupposes something that can see more than one device at once. Today's Android app cannot: it has exactly one `GoogleWorkspaceTokenStore`, on one phone (or tablet), with no channel to any other device. Building a "Device Management Center" screen against that would mean either showing a fake list with one entry pretending to be a fleet, or wiring up a backend that doesn't exist (`ConnectionRepository`'s own docstring has flagged this exact gap since Sprint 9). Neither is acceptable under this codebase's standing rule against reported-but-not-real functionality. So: this document specifies the data model, the backend API, and the exact revoke mechanism now, so that the day AUTH-001's backend bridge lands, this is a translation exercise, not a design exercise.

## 2. Device record — the shape that syncs

**Decision.** One row per device, per Owner account, holding metadata only — never a credential:

```
DeviceRecord {
  deviceId: String            // stable, generated once at first connect, never reused
  deviceName: String          // owner-editable, e.g. "Ankush's Pixel"
  platform: String            // "Android", "Desktop-macOS", "Web", ...
  lastActiveAt: Instant        // last time this device successfully called a Google API
  connectedServices: Set<String>   // scope names, e.g. gmail.readonly, calendar.readonly
  syncStatus: DeviceSyncStatus // HEALTHY / DEGRADED / NEEDS_REAUTH / DISCONNECTED
  connectedAt: Instant
}
```

This is deliberately the same set of fields Sprint 13's `GoogleWorkspaceTokenStore.currentConnectionInfo()` already computes locally today (`accountEmail`, `grantedScopes`, `lastSyncAt`, `lastTokenRefreshAt`) plus two new ones this document adds: `deviceId` and `deviceName`. **Nothing about a refresh token, access token, or any other secret appears in this record** — that's the whole point of AUTH-001 Layer 2, restated here as a concrete schema instead of a principle.

**What each device actually pushes.** On every successful sync, a device `PUT`s its own `DeviceRecord` (identified by its own `deviceId`) to the backend. It can only ever write its own row — the backend rejects a write to a `deviceId` the calling credential doesn't own. This is what makes "each device maintains its own secure credentials while the backend synchronizes only non-sensitive metadata" true by construction rather than by policy: the backend literally never receives a token, so there's nothing to synchronize it *from*.

## 3. Backend API surface

**Decision.** Five endpoints, all under the Owner's authenticated session with the (not-yet-existing) JARVIS backend:

| Endpoint | Effect |
|---|---|
| `GET /devices` | List every `DeviceRecord` for the Owner — this is "Display every authorized device." |
| `PATCH /devices/{deviceId}` `{deviceName}` | Rename. Metadata-only write, no device involvement needed — the owning device picks up the new name on its next sync. |
| `POST /devices/{deviceId}/disconnect` | Marks the record `DISCONNECTED` and queues a disconnect command (Section 4) for that device. Does not touch any other device's row. |
| `POST /devices/{deviceId}/revoke` | Queues a **revoke** command (Section 4) for that specific device, and that device only. |
| `GET /devices/{deviceId}/approval-history` | Returns the existing `ApprovalAuditRecord` list, filtered by this device's id — see Section 5, this needs no new storage. |

**Alternative considered — one `POST /devices/{deviceId}/action` endpoint with an action-type body field.** Rejected: five narrow, explicit endpoints are individually easier to permission-check and individually easier to audit-log than one endpoint whose effect depends on a body field — same "sharp seams over convenient shortcuts" reasoning JARVIS-001 already established, applied to an API surface instead of a class boundary.

## 4. Revoking one device without touching the others — the actual hard part

**The problem, stated precisely.** All devices share one Google OAuth Client ID (AUTH-001 Section 2 — Android phone and tablet are literally the same client type). Google's own consent-management page (`myaccount.google.com/permissions`) generally groups grants by *(user, client, scope-set)*, not by individual device — so an Owner clicking "Remove Access" there for "JARVIS" may revoke every device's refresh token for that client at once, not just one. That's a real constraint of Google's system, not a JARVIS design choice, and this document does not pretend otherwise.

**Decision — revoke-by-command, not revoke-by-token.** The backend never holds a refresh token to call Google's revoke endpoint with (Layer 1 forbids that). Instead:

1. Owner taps "Revoke" for device B on device A.
2. Device A calls `POST /devices/{B}/revoke`.
3. The backend marks B's record `PENDING_REVOKE` and, the next time device B checks in (a lightweight poll or push notification — mechanism TBD with the backend's own transport, out of scope for this document), delivers that as a queued command.
4. Device B, and only device B, calls `AuthenticationProvider.revoke()` — the exact method AUTH-001 already put on the shared interface — using **its own, locally-held** refresh token. The token still never leaves device B.
5. Device B reports success back; the backend flips its record to `DISCONNECTED`.

**Why this satisfies "without affecting others."** Because the actual Google-side revoke call is always made with one specific device's own refresh token, exactly the same call `revoke()` already makes today for a single device — nothing in that call can reach any other device's grant. The risk flagged above (Google's own account page revoking the whole client at once) is a *different, Owner-initiated* path outside JARVIS entirely; JARVIS's own Revoke button never takes that path.

**Consequence / honest gap.** Step 3 requires device B to be reachable (online, JARVIS running or at least backgroundable) to actually execute its own revoke. A revoke request for an offline, lost, or wiped device sits `PENDING_REVOKE` until that device checks in — which may be never, if the device is gone for good. The mitigating control is the same as Google's own: the Owner can always fall back to Google's account permissions page for a "this device is gone, kill it at the source" scenario, accepting the coarser blast radius described above as the tradeoff for not being able to reach an offline device otherwise. This should be stated in-product ("revoking an offline device queues the request; for a lost device, also remove access at your Google Account") rather than silently left as a confusing pending state.

## 5. Approval history needs no new storage

**Decision.** `ApprovalAuditRecord.provider` and `.metadata` (both already present, both already designed — see that class's own docstring: *"present in the shape now so a real backend's richer audit payload has somewhere to land later without another schema change"*) are exactly what device approval history needs. Once AUTH-001 Section 5's device-scoped `CONNECTION_REQUEST` approvals exist, `GET /devices/{deviceId}/approval-history` is a filter over the same append-only log `ConnectionsViewModel.auditFor()` already reads today for connections — same query shape, same UI pattern (`ConnectionsScreen`'s existing audit dialog), applied to a device id instead of a connection id.

## 6. What ships now vs. later

| Item | Status |
|---|---|
| `DeviceRecord` schema (Section 2) | Specified |
| Backend API (Section 3) | Specified, not built — no backend exists |
| Revoke-by-command mechanism (Section 4) | Specified — this is the part most worth building carefully once the backend exists, given the Google-side constraint it works around |
| Device Management Center UI | Not designed screen-by-screen yet — deferred until the API it renders exists, so the UI isn't designed against a guessed contract |
| **`deviceId` + owner-editable `deviceName` on this device, today** | **Built with this delivery** (Section 7) — the one piece that doesn't need a backend, and the exact field pair `DeviceRecord` needs later |

## 7. What's real today

`GoogleWorkspaceTokenStore` now generates and stores a stable `deviceId` (a random UUID, created once, never regenerated) and an Owner-editable `deviceName` (defaulting to the Android device's own model name via `android.os.Build.MODEL`), surfaced in the existing Google Workspace card in Settings with a rename action. This is genuinely useful standalone today (the Owner can label the phone "Ankush's Pixel" now), and it's precisely the two fields `DeviceRecord` will read from this device the day the backend sync client is written — no migration, no re-authorization, no schema translation later.
