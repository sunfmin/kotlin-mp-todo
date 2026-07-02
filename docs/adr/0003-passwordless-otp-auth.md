# Passwordless authentication via emailed OTP code

Accounts are identified by email address with no password. To sign in, a client submits an email; the server emails a short-lived 6-digit one-time code (OTP); the client submits the code; on success the server issues a short-lived access token plus a refresh token. Clients store these in each platform's secure storage (Keychain / Keystore / equivalent) and send the access token as a bearer token on every API call.

## Why

We considered email+password and social/OAuth. Passwordless OTP was chosen because the OTP flow is **identical on all four clients** (two screens: enter email, enter code) with zero per-platform deep-linking or provider-SDK integration — which preserves the shared-code investment that motivates using KMP. We rejected magic links specifically because they force a browser round-trip and native deep-link plumbing per platform. We rejected passwords to avoid storing/managing password hashes and the associated reset flows.

## Consequences

- The product has a **hard dependency on reliable transactional email**; email deliverability and latency are now on the critical login path.
- There is no password to fall back on; account recovery is inherently tied to email access.
- The server owns rate-limiting and expiry of OTP codes to prevent brute-force and abuse.
- Adding social login later is additive and does not invalidate this decision.
