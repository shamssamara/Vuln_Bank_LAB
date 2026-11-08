# تحديث كامل للمشروع — VulnBank (Windows + Android Studio)

## ما الذي تغير؟

- تصميم Login وDashboard السابق **محفوظ**، مع عبارة `Created by SHAMS SAMARA` بأسلوب أنيق في Login وHome وMore.
- صفحة Transfer تبحث عن مستفيد بالاسم، `@username` أو Account ID ثم تعرض `fullName`, `username`, `accountId`.
- زر Send Money يبقى قابلًا للضغط؛ إن لم تختر مستفيدًا أو أدخلت مبلغًا غير صالح يظهر السبب على الصفحة نفسها.
- مسار الخادم `GET /api/v1/recipients` موجود ضمن الحزمة؛ خطأ HTTP 404 يعني غالبًا أنك تشغّل صورة خادم قديمة أو أن Burp يشير إلى هدف آخر.
- `GET /health` يعيد حقل `backendVersion=2026.11-recipient-transfer-fix` لتمييز صورة الخادم الحالية.
- كل ثغرات التدريب السابقة (SQLi, Stored XSS, hardcoded key, API BOLA) محفوظة مع بيانات وهمية فقط.

## 1 — احفظ نسخة احتياطية ثم استبدل الملفات

فك ZIP في `D:\Mobile Project` بحيث يصبح المسار `D:\Mobile Project\VulnBankLab` واستبدل الملفات المتطابقة فقط. لا تحذف المجلد القديم أو مجلدات Gradle وAndroid Studio؛ **ولا تحذف Docker Volume**. قم بعمل نسخة احتياطية من المشروع قبل الاستبدال.

## 2 — تشغيل Docker والتحقق من عدم وجود خادم قديم

افتح PowerShell في جذر المشروع:

```powershell
cd "D:\Mobile Project\VulnBankLab"
docker compose up -d --build --force-recreate server
docker compose ps
python .\scripts\verify_update.py
```

إذا بقيت نسخة الخادم قديمة، أعد بناء صورة **الخادم فقط** (بدون حذف البيانات):

```powershell
docker compose build --no-cache server
docker compose up -d --force-recreate server
python .\scripts\verify_update.py
```

المفروض تظهر 4 رسائل `[PASS]`: صحة الخادم الحالي، Directory، Transfer، Accounts. الفحص لا يغيّر الأرصدة.

## 3 — Burp Suite

- Listener واحد على `127.0.0.1:8081`، و`Intercept is off` أثناء الاستخدام العادي.
- **ألغِ Redirect to host/port** على Listener `8081`، وألغِ أي Listener على `8080`.
- Docker backend: `127.0.0.1:8080` /health يجب أن يرجع JSON.
- لا تحتاج Frida ولا شهادة Burp لهذه النسخة HTTP المحلية.
- احذف إعداد البروكسي العام القديم من Android Emulator إن كان مفعّلًا:

```powershell
adb shell settings put global http_proxy :0
```

## 4 — Android Studio

`File → Sync Project with Gradle Files` ثم `Build → Rebuild Project` أو **Run app**. لأجهزة المحاكي يمكنك تثبيت APK الجديد من زر Run مباشرة.

عند التثبيت اليدوي فقط، ابحث عن الملف الصحيح ثم استخدم `-r -t` إذا كان test-only:

```powershell
adb install -r -t ".\app\build\intermediates\apk\debug\app-debug.apk"
adb shell am force-stop com.vulnbank.lab
adb shell monkey -p com.vulnbank.lab 1
```

## 5 — تجربة التحويل

سجل دخول `alice` مع `password123`، افتح Transfer، وابحث عن `bob` أو `@bob` أو `2`.
اختر `Bob Smith · @bob · Account #2` ثم أدخل مبلغًا وهميًا مثل `15.00` واضغط Send Money. تأكد من ظهور استجابة HTTP 201 في Burp وتحديث الرصيد.

تجربة BOLA **داخل المختبر المحلي فقط**: بعد اعتراض `POST /api/v1/transfers` في Burp Repeater يمكن تعديل `fromAccountId` إلى حساب وهمي آخر. هذه ثغرة مقصودة للتدريب، وليست ميزة للاستخدام الحقيقي.

## إصلاح الأخطاء

- `HTTP 404 (/api/v1/recipients)`: شغّل `python scripts\verify_update.py`، ثم أعد بناء صورة الخادم عند الحاجة.
- استجابة HTML بعنوان Burp: لا يوجد Backend على 8080 أو Burp Listener يعيد التوجيه خطأ. شغّل `docker compose ps` واختبر `curl.exe --noproxy "*" http://127.0.0.1:8080/health`.
- لا يعمل Send Money: اختر مستفيدًا من القائمة أولًا. الزر أصبح يعرض تنبيهًا على الشاشة بدل أن يكون معطّلًا بصمت.
- لا تحذف قاعدة PostgreSQL ولا تشغل `docker compose down -v` ما لم تكن تريد إعادة ضبط بيانات المختبر بالكامل.

**ملاحظة تحقق:** فحوصات الملفات لا تعني بناء APK أو تشغيل Docker. تأكد من `Gradle Build` واختبار REST على جهاز Windows.
