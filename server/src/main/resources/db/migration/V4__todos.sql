-- Slice 4: Todos within a List. A Todo belongs to exactly one List (list_id is
-- NOT NULL, FK CASCADE), carries a fractional order_key for manual ordering
-- (single-row move, no full re-index), and is never orphaned.

CREATE TABLE todos (
    id          UUID PRIMARY KEY,
    list_id     UUID NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
    title       VARCHAR(500) NOT NULL,
    description TEXT,
    due_date    DATE,
    completed   BOOLEAN NOT NULL DEFAULT FALSE,
    order_key   DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_todos_list_order ON todos (list_id, order_key);
