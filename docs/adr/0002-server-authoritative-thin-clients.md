# Server-authoritative thin clients (online-only, no offline)

The server owns the single authoritative datastore. All clients (Android, iOS, Desktop, Web) are thin: they render UI over the server API and hold only transient in-memory state — **they never persist domain data locally and do not work offline**. There is no sync engine, no local database, and no conflict resolution.

## Why

This is targeted at an always-connected usage context, where the operational cost of offline-first (local databases per platform, a sync protocol, conflict resolution, tombstones, clock ordering) is not justified. Rejecting offline-first removes the single largest source of complexity in a multiplatform todo app and keeps the domain authoritative in one place.

## Consequences

- Every user interaction that reads or writes domain data requires connectivity; clients must present clear loading and error states, not silent failures.
- Adding offline support later is **not** a drop-in change — it would reintroduce local persistence and sync and likely reopen this decision. We are deliberately trading that future option for present simplicity.
- The client data-access layer is a straight HTTP client against the server API; there is no repository abstraction hiding a local cache, because there is no cache.
