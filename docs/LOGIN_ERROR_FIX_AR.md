# تسجيل الدخول — رسالة خطأ مناسبة للمستخدم

- عندما يرجع المسار `POST /api/v1/auth/login` حالة `401`، تظهر فقط `Wrong username or password` في التطبيق.
- يستمر Burp Suite بعرض حالة HTTP 401 واستجابة JSON للتدريب.
- يظل خطأ الشبكة الحقيقي مختلفًا عن خطأ بيانات الدخول، حتى لا يتم إخفاء الأعطال على أنها كلمة مرور خاطئة.
- الخادم يعيد الرسالة العامة نفسها ضمن جسم JSON، بدون كشف ما إذا كان اسم المستخدم موجودًا.

## مهم: اختر مسار مشروع واحد

إذا كنت تستخدم نسخة `D:\Mobile Project\test\VulnBankLab`، نفّذ أوامر Docker من داخلها فقط. النسخة الأخرى `D:\Mobile Project\VulnBankLab` تستخدم اسم مشروع Docker نفسه وقد تستبدل صورة الخادم المشتركة.

```powershell
cd "D:\Mobile Project\test\VulnBankLab"
docker compose up -d --build --force-recreate server
python .\scripts\verify_update.py
```

عندما ينجح اختبار الخادم، افتح مشروع Android Studio من **المجلد نفسه** واختر Run `app` لإعادة تثبيت APK المحدث.

إذا ظهر `401` لبيانات تجريبية كنت تظنها صحيحة، فتحقق من القيم الفعلية داخل PostgreSQL؛ Docker Volumes لا تُعاد تهيئتها عند استبدال `init.sql`.

لا تستخدم `docker compose down -v` لأن هذا يحذف بيانات قاعدة المختبر.
