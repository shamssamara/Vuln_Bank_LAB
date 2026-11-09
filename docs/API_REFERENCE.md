# VulnBank LAB — API Reference

All routes below use **local HTTP only** and fictional data. Run both Docker services; do not publish beyond the Windows loopback host.

- Vulnerable: `http://127.0.0.1:8080`
- Secure comparison: `http://127.0.0.1:8082`
- Android emulator routes through Burp for vulnerable requests only (`10.0.2.2:8081`).
- `Idempotency-Key` is required only in Secure transfers; Vulnerable ignores it.

| Method | Route | Purpose |
|---|---|---|
| GET | `/health` | Mode and backend version |
| POST | `/api/v1/auth/login` | Login; returns sessionToken |
| POST | `/api/v1/auth/logout` | Secure-mode logout |
| GET | `/api/v1/customers` | Lab API customers (vulnerable key / secure session) |
| GET | `/api/v1/recipients` | Recipient directory |
| GET | `/api/v1/accounts/{id}` | Account details |
| GET | `/api/v1/transactions?account_id=1` | Account history |
| POST | `/api/v1/transfers` | Transfer synthetic JOD |
| GET/POST | `/api/v1/support` | Customer support |
| GET | `/api/v1/support/{id}` | Ticket detail |

## Test health

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8080/health
curl.exe --noproxy "*" http://127.0.0.1:8082/health
```

## Transfer request body

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 5.0,
  "note": "replay-demo"
}
```

Vulnerable additionally accepts its fictional `X-API-Key: VULNBANK_LAB_KEY_123`. Secure uses `Authorization: Bearer <demo-session-token>` from login and requires a stable `Idempotency-Key` for safe request retries.

Use `python scripts/verify_dual_mode.py` for non-mutating checks or `--run-replay` for deliberate fictional-money tests.
