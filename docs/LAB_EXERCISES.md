# VulnBank Lab — practical learning guide

**Scope:** Your local Docker + Android Emulator only. All identities, keys and balances are artificial.

## 0. Recon and APK inspection
1. Build APK: `./gradlew :app:assembleDebug` (or Android Studio's Build APK option).
2. `adb shell pm list packages | grep vulnbank`
3. `adb shell pm path com.vulnbank.lab` then `adb pull <full-base.apk-path>`.
4. Open APK with `jadx-gui`; find `LabSecrets`, `RootGate`, `AuthGate`, `CertificatePinner`, and `10.0.2.2`.
5. Review `AndroidManifest.xml` and `network_security_config.xml` using jadx/apktool.

## 1. SQL injection / authentication bypass
- Normal demo credentials: `alice / password123`.
- Vulnerable field: username.
- Test username: `' OR 1=1 --`
- Password: `anything`.
- Expected: green success and a table/list of multiple fabricated users; `bypassDetected=true`.
- Root cause: interpolating user-controlled text into SQL without parameter binding.
- Real mitigation: use prepared statements and server-side session validation.

## 2. Stored XSS via support WebView
- Go to **Support**. Subject `Training XSS`.
- Message: `<script>alert("XSS");</script>`
- Submit; expect green request-sent banner.
- Tap **Load support tickets** if needed, then open that ticket.
- Expected: native JS alert dialog from deliberately insecure WebView rendering.
- Root cause: inserting untrusted HTML into a JavaScript-enabled WebView without encoding.
- Real mitigation: render as plain text, HTML-encode, and disable JS when unnecessary.

## 3. Hardcoded secret / API key
- Find `VULNBANK_LAB_KEY_123` using JADX strings or `LabSecrets.getLabApiKey()`.
- Request: `GET https://localhost:8443/api/v1/customers`.
- Header: `X-API-Key: VULNBANK_LAB_KEY_123`.
- API responds with 12 fabricated users.
- Request without the header should return HTTP 401.
- Root cause: any APK-level key can be recovered and reused; secrets belong on server.

## 4. IDOR / BOLA
- Request `GET /api/v1/accounts/1`, then `/api/v1/accounts/2` with same demo API key.
- Compare owner IDs. Server does not check object ownership.
- Request `GET /api/v1/transactions?account_id=2`.
- Real mitigation: verify permissions against authenticated user for every object.

## 5. SSL pinning bypass
- Ktor server uses HTTPS on `8443` and includes a self-signed lab certificate, trusted by the Android app. OkHttp also pins its public key.
- On a **debug** emulator, install Burp's CA as a user certificate and configure proxy to Burp listener.
- Without bypass, Burp interception should cause the app's pinned HTTPS requests to fail.
- Run Frida: `frida -U -f com.vulnbank.lab -l frida_scripts/ssl_pinning_bypass.js`.
- Retry login/API lab with Burp: pinned certificate check is bypassed. The Burp CA must *still* be trusted; pin bypass is not a replacement for trust configuration.
- Never use the included demo TLS certificate on public services.

## 6. Root detection bypass
- Enable **Simulate rooted emulator** in the app's Frida tab.
- Press **Check root status**: should say ROOT DETECTED.
- Start `frida -U -f com.vulnbank.lab -l frida_scripts/root_detection_bypass.js`.
- Reopen Frida tab and press check again: now says No root detected.

## 7. Client-side authentication bypass
- Press **Test isAuthenticated()** before hook: VIP vault LOCKED.
- Run `frida -U -f com.vulnbank.lab -l frida_scripts/auth_bypass.js`.
- Recheck: VIP vault UNLOCKED. This is only a client-side visual gate, not a server-authenticated banking session.

## 8. Dynamic API-key extraction
- Start `frida -U -f com.vulnbank.lab -l frida_scripts/runtime_key_logger.js`.
- Press **Call getLabApiKey()** in Frida tab.
- Observe Frida console logging the artificial key.

## 9. All hooks in one command
`frida -U -f com.vulnbank.lab -l frida_scripts/all_lab_hooks.js`

## Reset after labs
- `docker compose down -v && docker compose up -d --build`.
- Resetting deletes fabricated tickets/transactions and reseeds the fake DB.
