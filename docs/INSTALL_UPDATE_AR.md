# تثبيت النسخة الكاملة

فك المشروع من ZIP ثم افتح مجلد `VulnBankLab` في Android Studio. اقرأ `START_HERE_AR.md`.

لا تستبدل أو تحذف بياناتك القديمة دون نسخة احتياطية؛ لا تستخدم `docker compose down -v`.

تشغيل: `docker compose up -d --build` ثم `python scripts/verify_dual_mode.py` وتشغيل التطبيق.

متطلبات: Android SDK 36، Java 17+، Docker Desktop، Burp للاستعراض في وضع Vulnerable فقط.
