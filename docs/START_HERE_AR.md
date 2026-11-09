# ابدأ من هنا — VulnBank LAB الحالي

هذه هي النسخة المعتمدة: **Vulnerable + Secure + Transaction Replay**. تم إلغاء الاعتماد على Frida والشهادات من بيئة HTTP المحلية.

## تشغيل Windows

```powershell
docker compose up -d --build
docker compose ps
python .\scripts\verify_dual_mode.py
```

- Vulnerable backend: `http://127.0.0.1:8080` (عبر Burp `127.0.0.1:8081` على Windows والمحاكي `10.0.2.2:8081`).
- Secure comparison backend: `http://127.0.0.1:8082` (مباشرة من المحاكي `10.0.2.2:8082`).
- البيانات موجودة على Docker volumes منفصلة. لا تستخدم `docker compose down -v` أثناء التشغيل العادي.
- افتح الجذر `VulnBankLab` في Android Studio وشغّل إعداد app، ثم اختر وضع الأمان من أول شاشة.
- بيانات العرض الوهمية `alice / password123`.

راجع: `DUAL_MODE_REPLAY_AR.md` للمقارنة والتكرار، و`BURP_NO_CERT_AR.md` لإعداد Burp.

**مهم:** وضع Secure تعليمي فقط؛ HTTP غير مشفر، ولا يجوز نشر أي من الخادمين على الإنترنت.
