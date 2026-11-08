# تحديث واجهة VulnBank + تحويلات الأموال

هذا تحديث مصدر Kotlin/Ktor لمشروع VulnBankLab الحالي، وليس APK جاهزًا. يحافظ على منافذ Burp الحالية وبيانات PostgreSQL.

## قبل التعديل

احتفظ بنسخة من مجلد `D:\Mobile Project\VulnBankLab`، أو على الأقل الملفات البرمجية التي ستستبدلها.

## تثبيت Patch ZIP

1. فك ضغط `VulnBank_Premium_UI_Transfer_Patch.zip` **داخل** `D:\Mobile Project\VulnBankLab`.
2. عندما يسألك Windows عن الملفات الموجودة، اختر **Replace** (لا تحذف مجلد المشروع أو `compose.yaml`).
3. من PowerShell:

```powershell
cd "D:\Mobile Project\VulnBankLab"
docker compose up -d --build
docker compose ps
curl.exe --noproxy "*" http://127.0.0.1:8080/health
python .\scripts\smoke_test.py
```

4. افتح Android Studio، ثم **Sync Project with Gradle Files** (أضفنا مكتبة أيقونات Material)، ثم اختر **Run app** على المحاكي.
5. إذا رغبت بتثبيت APK يدويًا بعد نجاح البناء:

```powershell
adb install -r -t "D:\Mobile Project\VulnBankLab\app\build\intermediates\apk\debug\app-debug.apk"
adb shell am force-stop com.vulnbank.lab
adb shell monkey -p com.vulnbank.lab 1
```

## اختبار Burp

- افتح Burp مع Listener يعمل على `127.0.0.1:8081`.
- لا تشغّل Frida ولا تحتاج إلى تثبيت CA.
- تطبيق Kotlin يستخدم Proxy داخلي `10.0.2.2:8081`، وBackend المحلي `127.0.0.1:8080`.
- سجّل دخول `alice` / `password123` ثم ادخل إلى **Transfer**. افتح Burp HTTP history أو فعّل Intercept لرؤية `POST /api/v1/transfers`.
- لتجربة ثغرة الحساب المرسل اقرأ `docs/TRANSFER_BURP_AR.md`.

> **مهم جدًا:** لا تستخدم `docker compose down -v` ولا تحذف مجلد/Volume قاعدة البيانات. الجدول الجديد يُنشأ تلقائيًا حين يعمل الخادم حتى إن كانت البيانات محفوظة من النسخة القديمة.

## ما الذي لم يتغيّر؟

- ثغرة SQL Injection في Login.
- Stored XSS في تذاكر Support.
- API key داخل تطبيق Android لاختبار JADX.
- API Lab، وواجهات Account/Transactions القديمة، وفحص الصلاحيات المحلي.
- HTTP/Burp على المنفذ 8081 (بدون شهادة وبدون Frida).

كل الأرصدة والمستخدمين وهميون. هذه النسخة الضعيفة ليست للنشر على الشبكات العامة.
