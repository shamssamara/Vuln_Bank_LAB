## Dual security mode and transaction replay

VulnBank LAB features separate **Vulnerable** (orange) and **Secure** (green) educational environments, each with an isolated Ktor backend and PostgreSQL volume. Vulnerable preserves intentional SQLi, XSS and authorization flaws and now demonstrates duplicated transfers; Secure applies parameterized SQL, session-based ownership checks and database-backed transfer idempotency. The comparison is local HTTP only and is not production security.

See [Dual Mode & Replay Guide](DUAL_MODE_REPLAY_AR.md).
