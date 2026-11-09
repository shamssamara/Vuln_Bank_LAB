# VulnBank LAB — Exercises (Vulnerable vs Secure)

**Training with synthetic data only. All backends must remain loopback-only.**

1. **Login SQL Injection** — Compare `alice/password123` with a classic username injection (`' OR 1=1 --`). Vulnerable demonstrates faulty SQL construction; Secure uses parameter binding and verifies password hashes.
2. **Object authorization** — Authenticate as Alice and request account `2`. Vulnerable permits demonstration of BOLA; Secure denies unauthorized account access.
3. **Client API key** — Inspect the Android APK and calls to `/api/v1/customers`. The vulnerable client holds a demo key, while Secure relies on a session and denies the full customer directory.
4. **Stored XSS** — Enter `<script>alert("XSS");</script>` in a fictional Support ticket. Vulnerable renders untrusted HTML in WebView; Secure uses text rendering.
5. **Transfer sender BOLA** — In Burp Repeater (vulnerable mode), change `fromAccountId` while holding other transfer fields fixed and observe missing ownership validation. Secure enforces ownership on the server.
6. **Transaction replay** — Run API Lab → Transaction Replay & Idempotency Lab. Two identical POSTs with the same Idempotency-Key should debit twice in Vulnerable and once in Secure.
7. **Concurrent replay** — Run `python scripts/verify_dual_mode.py --run-concurrent` and verify a unique transfer ID and one fictional debit.

**Caution:** replay/transfer exercises change stored demo balances. Do not run a data reset except when intentionally starting a fresh fictional lab. More information: `DUAL_MODE_REPLAY_AR.md`.
