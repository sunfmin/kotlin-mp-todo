# REST/JSON API with a shared Kotlin contract module

The client↔server API is REST over JSON. The request/response DTOs and shared domain enums (e.g. Role) live in a single shared Kotlin module (`common`) that both the Ktor server and all four clients depend on, (de)serialized with `kotlinx.serialization`. There is no code generation. Clients use the Ktor HTTP client; the server uses Ktor server.

## Why

Because the server is also Kotlin, the API contract can be one source of truth rather than hand-copied per client. Changing a DTO fails compilation in every client until updated, eliminating silent contract drift — the signature payoff of an all-Kotlin stack. We rejected plain REST with hand-written per-client models (drift-prone) and rejected a typed RPC framework such as gRPC (adds a schema/codegen toolchain and needs a grpc-web proxy, which fights the Web target). Plain JSON keeps the API legible to the Compose HTML web client and any future non-Kotlin consumer.

## Consequences

- The `common` module must stay free of platform- and framework-specific dependencies so it can compile for JVM (server, Android, Desktop), Native (iOS), and JS/Wasm (Web).
- The API contract and any shared validation logic have exactly one home; treat `common` as the boundary and keep server-internal and client-internal types out of it.
- Serialization format is JSON via `kotlinx.serialization`; changing it later is a cross-cutting change touching every client.
