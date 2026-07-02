# Layered Gradle module structure

The monorepo is organized into layers that separate the view-agnostic client from the shared UI, so the Web client can reuse client logic without depending on the Compose widget tree:

```
/
├── common/          # API DTOs, domain enums, serialization — JVM/Native/JS/Wasm  (ADR-0005)
├── client-core/     # Ktor HTTP client, SSE, auth/token storage, ViewModels — no UI (ADR-0001, 0006)
├── ui-compose/      # shared Compose Multiplatform UI — Android/iOS/Desktop only  (ADR-0001)
├── apps/
│   ├── android/     # thin entry point → ui-compose
│   ├── ios/         # thin entry point → ui-compose (+ SwiftUI escape hatches)
│   ├── desktop/     # thin entry point → ui-compose
│   └── web/         # Compose HTML UI → client-core (NOT ui-compose)   (ADR-0001)
└── server/          # Ktor + Exposed + Postgres → common                (ADR-0002, 0005, 0007)
```

## Why

ADR-0001 splits the view layer: three native clients share a Compose UI, while Web has its own Compose HTML UI but reuses everything below the view layer. A single combined `shared` module would force the Web client to depend on the Compose widget tree it must not use. Separating `client-core` (ViewModels, networking, auth — consumed by all four clients) from `ui-compose` (widgets — consumed only by the three native clients) makes the Web boundary a compile-time fact rather than a convention. The layout stays flat (no per-feature modules) to avoid Gradle overhead premature for v1.

## Consequences

- `apps/web` depends on `client-core` and must **not** depend on `ui-compose`; enforce this dependency direction in build config.
- `common` depends on nothing app-specific; `client-core` depends on `common`; `ui-compose` depends on `client-core`; `server` depends on `common` only.
- Feature-sliced modules can be introduced later within `client-core`/`ui-compose` without disturbing this top-level shape.
