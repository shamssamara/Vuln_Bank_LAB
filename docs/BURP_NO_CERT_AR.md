# VulnBank — Burp Direct HTTP (no Frida / no certificate)

This project uses intentionally local HTTP for demonstrations. No TLS pinning or Burp CA installation is required. Do not expose HTTP endpoints to a public or untrusted network.

1. Start Docker: `docker compose up -d --build` and `docker compose ps`.
2. Confirm health: `curl.exe --noproxy "*" http://127.0.0.1:8080/health`.
3. Start Burp Suite HTTP Proxy Listener on Windows at **127.0.0.1:8081**. Disable **Redirect to host/port**. Set Intercept off for normal use.
4. The Android Emulator connects to `10.0.2.2:8081` with an **explicit OkHttp proxy**. Do not set emulator global proxy. To reset it: `adb shell settings put global http_proxy :0`.
5. In Burp → HTTP History, inspect `/api/v1/...` requests after selecting **Vulnerable** in the app.
6. **Secure** mode bypasses Burp and calls the separate secure Ktor instance at **10.0.2.2:8082**.

**No Frida, no certificate, no HTTPS port 8443 in this edition.**
