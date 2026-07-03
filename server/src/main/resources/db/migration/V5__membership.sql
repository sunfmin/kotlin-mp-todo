-- Slice 5: sharing & membership via revocable Invite Link (ADR-0004).
--
-- memberships holds the EDITOR members of a List; the single Owner stays in
-- lists.owner_id (so the "exactly one Owner" invariant, ADR-0009, is structural
-- and an owner is never also an editor row). A (list_id, user_id) is unique, so
-- a User is a member of a List at most once. Both FKs CASCADE, so ending a List
-- or a User cleans up memberships automatically.
CREATE TABLE memberships (
    list_id    UUID NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (list_id, user_id)
);

CREATE INDEX idx_memberships_user ON memberships (user_id);

-- One Invite Link row per generation. Regenerating marks the prior row inactive
-- and inserts a new active one; revoking marks the active row inactive. The
-- partial unique index enforces "at most one active link per List" at the DB
-- level. token is the opaque value embedded in the shareable URL.
CREATE TABLE invite_links (
    token      UUID PRIMARY KEY,
    list_id    UUID NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_invite_links_one_active ON invite_links (list_id) WHERE active;
