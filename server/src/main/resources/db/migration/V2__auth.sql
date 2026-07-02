-- Slice 2: passwordless OTP auth (ADR-0003).

CREATE TABLE users (
    id         UUID PRIMARY KEY,
    email      VARCHAR(320) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- At most one active code per email (PK on email; replaced on each request).
CREATE TABLE otp_codes (
    email      VARCHAR(320) PRIMARY KEY,
    code_hash  VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts   INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Opaque refresh tokens, stored hashed.
CREATE TABLE refresh_tokens (
    token_hash VARCHAR(128) PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Log of OTP requests, used for rate limiting.
CREATE TABLE otp_requests (
    id           BIGSERIAL PRIMARY KEY,
    email        VARCHAR(320) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_requests_email_time ON otp_requests (email, requested_at);
