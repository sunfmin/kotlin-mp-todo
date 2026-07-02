-- Slice 3: private Lists owned by a User (ADR-0009: exactly one Owner).
-- owner_id is NOT NULL, so a List is never ownerless. Editors arrive in slice 5.

CREATE TABLE lists (
    id         UUID PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_lists_owner ON lists (owner_id);
