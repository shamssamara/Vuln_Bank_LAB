# VulnBank localhost API reference

Android app: HTTP through the explicit Burp proxy `10.0.2.2:8081` to `http://127.0.0.1:8080`.
Windows local backend: `http://127.0.0.1:8080` (optional legacy HTTPS: `https://localhost:8443`).
These are development-only addresses; never publish this insecure API.

| Method | Endpoint | Required | Function | Deliberate weakness |
|---|---|---|---|---|
| GET | `/health` | none | liveness | none |
| POST | `/api/v1/auth/login` | JSON username/password | simulated login | SQL injection |
| GET | `/api/v1/customers` | `X-API-Key` | list customers | hardcoded client key |
| GET | `/api/v1/recipients` | `X-API-Key` | list full names, @usernames and account IDs | demo recipient discovery |
| GET | `/api/v1/accounts/{id}` | `X-API-Key` | get account by ID | BOLA/IDOR |
| GET | `/api/v1/transactions?account_id={id}` | `X-API-Key` | transaction list | BOLA/IDOR |
| POST | `/api/v1/transfers` | `X-API-Key` + JSON | real synthetic balance transfer | **sender-account ownership check missing** |
| POST | `/api/v1/support` | JSON userId/subject/message | create support ticket | unsanitized stored HTML |
| GET | `/api/v1/support` | none | list tickets | deliberately open demo |
| GET | `/api/v1/support/{id}` | none | ticket details | deliberately open demo |

Example body for login:

```json
{"username":"alice","password":"password123"}
```

Example ticket:

```json
{"userId":1,"subject":"Demo XSS","message":"<script>alert(\"XSS\");</script>"}
```

Use trusted example: `curl --cacert server/certs/lab-cert.pem https://localhost:8443/health`

All financial, authentication and customer information is **dummy data**.

## Transfer money — intentionally vulnerable server-side authorization

This endpoint **really changes** the synthetic account balances and writes debit and credit rows atomically. It is not just a UI alert.

Normal request from the signed-in user `alice` (account ID 1) to `bob` (account ID 2):

```http
POST /api/v1/transfers HTTP/1.1
Host: 127.0.0.1:8080
Content-Type: application/json
X-API-Key: VULNBANK_LAB_KEY_123

{"fromAccountId":1,"toAccountId":2,"amount":15.00,"note":"Gift"}
```

To demonstrate the IDOR/BOLA issue within this isolated synthetic-data lab, intercept the request in Burp Repeater and change it to:

```json
{"fromAccountId":2,"toAccountId":1,"amount":15.00,"note":"Modified request"}
```

Now fictional user Bob's account is debited and Alice's account is credited, because the **server trusts `fromAccountId` without verifying ownership**. The API key authenticates only the test client, not the sender. Successful responses are HTTP 201 with a `transferId`; insufficient fictional balance returns HTTP 409. Transfers are limited to JOD 2,000 per request and never leave this local database.

The persistent table `bank_transfers` is created on server startup even when you retain an older Docker PostgreSQL volume. **Do not run `docker compose down -v` to apply the update.**

## Recipient lookup (username + account ID)

The transfer page now has a searchable recipient directory. You can type a full name,
`@username` (e.g. `@bob`), or an account number and select a result. It shows
**Bob Smith — @bob — Account #2**. The selected account ID is used as the
`toAccountId` in the existing transfer request. The sender is shown as
**Alice Jordan — @alice — Account #1**.

`GET /api/v1/recipients` returns entries of the form:

```json
{"accountId":2,"userId":2,"username":"bob","fullName":"Bob Smith","accountType":"Savings"}
```

Responses from `POST /api/v1/transfers` now also contain
`fromUsername`, `toUsername`, `fromFullName`, and `toFullName` for a readable receipt.
The transfer still uses IDs as authoritative identifiers, so the intentional
**BOLA/IDOR** exercise with `fromAccountId` remains exactly the same.
No database migration is needed: the username and full name are read from `users`.

### Optional readable handles in transfer JSON

The app sends both numeric account IDs and their usernames, for example:

```json
{
  "fromAccountId": 1, "fromUsername": "alice",
  "toAccountId": 2, "toUsername": "bob",
  "amount": 15.00, "note": "Dinner"
}
```

The server accepts older requests without the `*Username` fields. These fields
are **presentation metadata only**: they are not trusted or checked for authorization.
The response gets the real names from the database and includes both IDs and handles.
In Burp, changing `fromAccountId` to 2 while leaving `fromUsername` as `alice`
illustrates why client-supplied display names cannot substitute for server-side ownership checks.
