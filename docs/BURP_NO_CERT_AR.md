# تشغيل VulnBank مع Burp مباشرة — بدون Frida أو شهادة

هذا الإصدار مخصص **لمختبر محلي ببيانات وهمية فقط**. التطبيق يستخدم **HTTP غير مشفر** عمدًا حتى يستطيع Burp Suite قراءة الطلبات دون تثبيت Burp CA أو تجاوز SSL Pinning.

## طريقة الاتصال

1. شغّل Docker Desktop وPowerShell داخل مجلد المشروع:
   ```powershell
   docker compose up -d --build
   docker compose ps
   ```
   يجب أن يظهر: `127.0.0.1:8080->8080/tcp` للخادم، و `127.0.0.1:5433->5432/tcp` لقاعدة البيانات، والمنفذ 8443 القديم إن كان مفعلاً.
2. شغّل Burp Suite على Windows. في `Proxy > Proxy settings` اجعل Listener العادي على **127.0.0.1:8081**، بدون Redirect to host/port؛ و `Intercept off`. لا تستخدم Listener 8082 المخصص سابقًا لإعادة توجيه HTTPS.
3. شغّل Android Emulator وتطبيق VulnBank LAB من Android Studio (Run > app). التطبيق يضبط Proxy خاصًا به داخل `BankApi.kt`، فلا تحتاج تعديل إعدادات المحاكي أو تركيب شهادة.
4. افتح VulnBank واضغط `GET /health` أو سجّل الدخول. افتح `Burp > Proxy > HTTP history`. ستظهر الطلبات والردود كـ **HTTP plaintext**؛ ومنها `POST /api/v1/auth/login`.
5. للتأكد من الخادم على Windows:
   ```powershell
   curl.exe --noproxy "*" http://127.0.0.1:8080/health
   ```
   يجب أن يُرجع JSON وفيه `"status": "ok"`.

## كيف تعمل عناوين الشبكة؟

- عنوان Proxy في المحاكي: `10.0.2.2:8081` → Burp على Windows.
- هدف HTTP في التطبيق: `http://127.0.0.1:8080` → يرسله OkHttp **من خلال Burp**، فيحل Burp العنوان على Windows، ليصل إلى Ktor المحلي عبر Docker.
- تأكد أن Burp Listener 8081 ليس فيه Redirect.
- لا حاجة لتشغيل Frida أو `frida-server`، ولا شهادة Burp CA.
- يبقى HTTPS على 8443 اختيارياً لاختبارات الخلفية القديمة، لكن التطبيق نفسه لا يستخدمه.

## إذا لم تظهر الطلبات

- تحقق من أن Burp Listener على `127.0.0.1:8081` هو `Running`، وأن `Intercept off`.
- جرب PowerShell: `Test-NetConnection 127.0.0.1 -Port 8081` و `Test-NetConnection 127.0.0.1 -Port 8080`.
- افحص `docker compose logs --tail=50 server`.
- اختر `Build > Rebuild Project` في Android Studio ثم شغّل التطبيق لتحديث BuildConfig.
- أخطاء `certificate_unknown` تخص عمليات HTTPS القديمة أو تطبيقات أخرى داخل المحاكي، ولا تنطبق على اتصالات VulnBank HTTP الجديدة.
- إذا كنت قد ضبطت Proxy عالمي للمحاكي وأردت إزالته: `adb shell settings put global http_proxy :0`. الاتصال في VulnBank سيستمر عبر Burp لأن OkHttp فيه Proxy صريح.

## تنبيه

HTTP ينقل البيانات دون تشفير. لا تستعمل هذه الإعدادات أو الـ API Key أو بيانات اعتماد حقيقية خارج مختبرك. جميع منافذ Docker المنشورة مقيدة على `127.0.0.1` الخاص بـ Windows.
