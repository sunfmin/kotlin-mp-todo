# Real-time list updates via Server-Sent Events (push-only)

Clients viewing a List subscribe to a Server-Sent Events (SSE) stream over which the server pushes "List X changed" notifications, prompting a targeted refetch. All writes continue to go through the REST API and the shared-DTO contract — SSE is a one-way server→client notification channel only, never a write path.

## Why

Collaborative lists need near-instant visibility of other members' changes, and we chose real-time push over polling. SSE was chosen over WebSockets because it is one-way (which is all we need — writes stay on REST), runs over plain HTTP, and has protocol-level auto-reconnect, keeping the server simpler than a bidirectional socket layer.

## Consequences

- **Platform risk:** Ktor's multiplatform *client* SSE support is less battle-tested on Kotlin/Native (iOS) and Kotlin/Wasm (Web) than WebSockets. If SSE proves unreliable on those targets, the fallback is a **push-only WebSocket** carrying the same "List X changed" notifications — this changes only the client/server transport layer, not the domain or the REST write path.
- The notification is a lightweight signal, not the changed data; clients refetch via REST, keeping one authoritative read path.
- The server must scope each SSE stream to lists the authenticated user is a member of, and tear streams down on membership revocation.
