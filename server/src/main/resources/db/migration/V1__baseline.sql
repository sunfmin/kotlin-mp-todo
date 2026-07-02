-- Baseline migration for the walking skeleton (slice 1).
-- Records server bootstrap metadata; domain tables (users, lists, todos,
-- memberships, invite links) are introduced by later slices' migrations.
CREATE TABLE server_info (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256) NOT NULL
);

INSERT INTO server_info (key, value) VALUES ('schema_baseline', 'slice-1');
