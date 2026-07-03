-- Slice 6: Todo assignment (ADR-0009: an Assignee is always a current member of
-- the Todo's List). assignee_id is a nullable FK to users; ON DELETE SET NULL so
-- a deleted account leaves its Todos unassigned rather than orphaning them. The
-- "assignee must be a current member" rule is enforced in the service on write;
-- slice 7 adds auto-unassign when a member leaves.

ALTER TABLE todos
    ADD COLUMN assignee_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_todos_assignee ON todos (assignee_id);
