# Server persistence: PostgreSQL via Exposed

The server persists all authoritative data in PostgreSQL, accessed through JetBrains Exposed (typed Kotlin SQL DSL). Schema migrations are managed explicitly (Flyway or Exposed migrations).

## Why

The domain is inherently relational with integrity constraints worth enforcing in the database itself — List ownership, Membership uniqueness, "assignee must be a member of the Todo's list," and at most one active Invite Link per List. Postgres enforces these with foreign keys and constraints and scales to a real multi-user server, unlike file-based SQLite. Exposed keeps the data layer in idiomatic Kotlin consistent with the rest of the stack, avoiding the Java-centric weight of Hibernate and the boilerplate of raw JDBC. A document/NoSQL store was rejected as a poor fit for relational, constraint-heavy data.

## Consequences

- Domain invariants are enforced at the database layer via constraints, not only in application code.
- The server requires a Postgres instance in every environment (local, CI, production); this is an operational dependency.
- Migrations are a first-class artifact and must accompany any schema-affecting change.
