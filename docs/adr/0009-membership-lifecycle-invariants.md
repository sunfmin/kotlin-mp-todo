# Membership lifecycle invariants

Two invariants hold at all times: **every List has exactly one Owner**, and **every Assignee is a current member of the Todo's List**. The following lifecycle rules enforce them:

1. **Member removed / leaves a List** → any Todos in that List assigned to them are automatically **unassigned**. Removal is never blocked on reassignment.
2. **Owner wants to leave a List** → they must first **transfer ownership** to another member, or delete the List. An Owner cannot simply leave, because a List may never be ownerless.
3. **User deletes their account** → deletion is **blocked** while they still own any *shared* List (a List with other members); they must transfer or delete each first. Solo-owned Lists (no other members) are deleted along with the account.

## Why

Collaboration makes these boundary cases reachable, and the sensible-but-unstated default in each case differs from the naive one. We chose auto-unassign over blocking removal (less friction), mandatory transfer over allowing ownerless lists (preserves the single-owner invariant), and blocking account deletion over silently deleting shared lists (avoids destroying data other members actively use). Recording the explicit *no-s* — no ownerless lists, no orphaned assignments, no silent deletion of shared lists — stops a future implementer from "simplifying" them away.

## Consequences

- Both the server (authoritative enforcement) and `client-core` (UI affordances and pre-checks) must respect these rules; the server is the final authority.
- The UI must surface an ownership-transfer flow and an account-deletion pre-check that lists blocking shared Lists.
