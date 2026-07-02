# Collaborative Todo

A multi-user, online-only todo product delivered across Android, iOS, Desktop, and Web clients backed by a shared Kotlin server. Users collaborate by sharing lists of todos.

## Language

**Todo**:
A single actionable item that a user wants to track. Belongs to exactly one List.
_Avoid_: Task, item, card, entry

**List**:
A named container of Todos, owned by a User and the unit of sharing/collaboration. Members and permissions attach to the List, not to individual Todos.
_Avoid_: Project, board, folder, group

**User**:
A person with an account who can own and be a member of Lists.
_Avoid_: Account, member (member is reserved for the membership relationship), person

**Membership**:
The relationship linking a User to a List they can access, carrying exactly one Role. A member is a User with a Membership in a given List.
_Avoid_: Collaborator, participant, access grant

**Owner**:
The Role held by exactly one member of a List — the creator. Can manage membership and delete the List, in addition to everything an Editor can do.
_Avoid_: Admin, creator

**Editor**:
The Role held by any non-owner member of a List. Can add, complete, edit, and delete Todos in the List, but cannot manage membership or delete the List. There is no read-only role.
_Avoid_: Collaborator, contributor, writer

**Assignee**:
The single member optionally responsible for a Todo. Must be a member of the Todo's List; a Todo with no assignee is unassigned.
_Avoid_: Owner (Owner is a List role), responsible party

**Invite Link**:
A revocable, multi-use link the Owner of a List generates; any signed-in User who follows it joins the List as an Editor. A List has at most one active Invite Link at a time (regenerating revokes the previous one); it may carry an optional expiry.
_Avoid_: Invitation, invite (implies a per-person accept step, which this is not), share code
