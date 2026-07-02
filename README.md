# Collaborative Todo

A production, online-only, multi-user **collaborative todo app** built with
[Kotlin Multiplatform](https://kotlinlang.org/multiplatform/) across five targets:
**Android, iOS, Desktop, Web, and Server**.

Users share **Lists** (the unit of collaboration) via revocable invite links; a
**Todo** lives in exactly one list and can be assigned to a member.

## Architecture at a glance

- **Server is the single authority** (Kotlin/Ktor + PostgreSQL/Exposed). Clients are
  thin and online-only — no local persistence, no offline sync.
- **Clients:** Android, iOS, and Desktop share a single **Compose Multiplatform** UI;
  **Web** has its own **Compose HTML** UI reusing all logic below the view layer.
- **API contract** is REST/JSON with a shared Kotlin `common` module
  (`kotlinx.serialization`) — one source of truth, compile-time-safe across all clients.
- **Real-time** list updates over **Server-Sent Events** (push-only; writes stay REST).
- **Auth** is passwordless: emailed one-time codes (OTP), server-issued access +
  refresh tokens.

## Module layout

```
common/       # API DTOs, domain enums, serialization  (JVM/Native/JS/Wasm)
client-core/  # Ktor HTTP client, SSE, auth, ViewModels — no UI
ui-compose/   # shared Compose Multiplatform UI — Android/iOS/Desktop
apps/
  android/    ios/    desktop/    web/   # thin entry points (web → client-core only)
server/       # Ktor + Exposed + Postgres
```

## Design docs

- [`CONTEXT.md`](./CONTEXT.md) — domain glossary (ubiquitous language)
- [`docs/adr/`](./docs/adr/) — architecture decision records

> Status: **design phase.** The domain and architecture are settled (see the ADRs);
> implementation has not started.
