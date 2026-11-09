# VulnBank LAB — المشروع الكامل (نسختا Vulnerable وSecure)

هذا ZIP مشروع كامل، **مش Patch**. فكّ الضغط في مجلد جديد ولا تستبدل مشروعك الحالي.

1. افتح مجلد `VulnBankLab` في Android Studio وثبّت Android SDK 36 إذا لزم.
2. افتح PowerShell داخل نفس المجلد وشغّل `docker compose up -d --build`.
3. افحص `docker compose ps`، ثم `python .\scripts\verify_dual_mode.py`.
4. فعّل Burp Suite HTTP listener على `127.0.0.1:8081` بدون Redirect للنسخة الضعيفة؛ الوضع الآمن يستخدم 8082 مباشرة.
5. شغّل المحاكي من Android Studio ثم اضغط Run. أول شاشة تطلب اختيار Vulnerable أو Secure.
6. الدخول: `alice` وكلمة المرور `password123` (بيانات وهمية).
7. افتح API Lab → Transaction Replay لاختبار إعادة إرسال تحويل وهمي.

**التحذير:** Secure محصّن للمقارنة التعليمية فقط؛ HTTP غير مشفّر. لا تفتح منافذ الخوادم للإنترنت.

هذا الإصدار يضم الشعار وصور GitHub ومجلدات Android + Ktor + PostgreSQL كاملة. لا يتطلب Frida أو شهادات CA.

إذا ظهرت مشكلة من `gradlew.bat`، جرّب `java -version` وتأكد من إعداد `local.properties` على جهازك.

## Kotlin compiler hotfix

تم تصحيح استنتاج نوع القوائم في `server/src/main/kotlin/com/vulnbank/server/SecureRoutes.kt`،
خاصة الخطأ عند السطر الذي يُنشئ `Transaction`.
بعد فك الضغط في مجلد منفصل، شغّل:

```powershell
docker compose up -d --build --force-recreate
docker compose ps
python .\scripts\verify_dual_mode.py
```

عدم ظهور `server_secure` بعد فشل البناء يعني أن Docker لم ينشر النسخة الجديدة،
ولا حاجة لحذف `dbdata` أو `dbdata_secure` أو تشغيل `down -v`.
