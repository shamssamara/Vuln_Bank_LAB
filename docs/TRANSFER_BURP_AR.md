# VulnBank — تحديث الواجهة + مختبر تحويل الأموال (Burp)

## ما الذي تغير؟

- `app/src/main/java/com/vulnbank/lab/ui/PremiumScreens.kt`: واجهات Kotlin/Jetpack Compose فعلية لتسجيل الدخول، لوحة الحسابات، التحويل.
- `app/src/main/res/drawable-nodpi/premium_shield.png`: عنصر زخرفي مُقتطع من التصميم المختار.
- `app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt`: ربط الصفحات الجديدة مع الوظائف القديمة.
- `app/src/main/java/com/vulnbank/lab/network/BankApi.kt`: دوال عرض الرصيد والحركات وإرسال التحويل.
- `server/src/main/kotlin/com/vulnbank/server/Main.kt`: `POST /api/v1/transfers` وتحديث الأرصدة والسجلات بعملية واحدة، مع غياب فحص ملكية المرسل **عمدًا**.
- `db/init.sql`: جدول التحويلات للبيئات الجديدة، والخادم ينشئه تلقائيًا للبيئات القائمة.

## التشغيل على Windows

```powershell
cd "D:\Mobile Project\VulnBankLab"
docker compose up -d --build
curl.exe --noproxy "*" http://127.0.0.1:8080/health
```

داخل Android Studio: اختر **Sync Project with Gradle Files**، ثم **Run app**. وإذا أردت تثبيت APK يدويًا:

```powershell
adb install -r -t ".\app\build\intermediates\apk\debug\app-debug.apk"
adb shell am force-stop com.vulnbank.lab
adb shell monkey -p com.vulnbank.lab 1
```

Burp: Listener `127.0.0.1:8081`، `Intercept off` للاستعراض الطبيعي؛ التطبيق يضبط Proxy داخليًا إلى `10.0.2.2:8081`. لا حاجة لـ Frida أو شهادة.

## أول تجربة تحويل طبيعية

1. ادخل باسم `alice` وكلمة المرور `password123` (الواجهة لا تعرض هذه البيانات كـ hint).
2. افتح Transfer، وابحث عن `bob` أو `@bob` أو `2`. اختر **Bob Smith · @bob · Account #2**، أدخل المبلغ `15.00`، واضغط Send Money.
3. راقب `POST /api/v1/transfers` في Burp HTTP history.
4. سترى حقل `fromAccountId` بقيمة `1` و`toAccountId` بقيمة `2`، وسيتغير رصيد الحسابين في قاعدة البيانات التجريبية.

## تجربة BOLA عن طريق Burp Repeater

أرسل الطلب إلى Repeater وعدّل جسم JSON إلى:

```json
{"fromAccountId":2,"toAccountId":1,"amount":15.00,"note":"Changed sender in Burp"}
```

مع الاحتفاظ بـ `X-API-Key: VULNBANK_LAB_KEY_123`. إذا نجح الاختبار، يرجع HTTP 201 ورقم التحويل؛ يتناقص رصيد Bob التجريبي ويزداد رصيد Alice. السبب: الخادم يتحقق من وجود حساب المُرسل ورصيده **ولا يتحقق من أن صاحب الجلسة يملكه**.

هذا مختبر بيانات وأرصدة وهمية بالكامل. حافظ عليه على Windows loopback فقط، ولا تربطه بأي بيانات أو حسابات بنكية حقيقية.

## التحقق بدون تغيير البيانات

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8080/health
```

للاختبار الآلي الذي يغيّر 1 JOD وهمي من Bob إلى Alice، شغل `python scripts/test_transfer_api.py` بعد تشغيل الحاويات.

**تحذير:** لا تستخدم `docker compose down -v` عند تطبيق التحديث؛ فهو يحذف بيانات PostgreSQL القديمة.

## عرض اسم المستفيد مع رقم الحساب

بعد التحديث تظهر خيارات المستفيدين على الشكل: `Bob Smith · @bob · Account #2`.
اختيار الاسم يحدد `toAccountId=2` تلقائيًا، ولا يحتاج المستخدم إدخال الرقم وحده.
بعد نجاح التحويل تظهر الأسماء في إيصال الردّ من الخادم، وتظهر الـ @username أيضًا
في وصف الحركة المالية. لا تتغير طريقة اختبار `fromAccountId` عبر Burp.

مثال طلب التحويل بعد التعديل:

```json
{"fromAccountId":1,"fromUsername":"alice","toAccountId":2,"toUsername":"bob","amount":15.00,"note":"Gift"}
```

الحقول `fromUsername` و`toUsername` اختيارية، لعرض اسم المستخدم بجانب رقم الحساب
في Burp. الخادم يقرأ أسماء أصحاب الحسابات الحقيقية من PostgreSQL لإظهار الإيصال.
**ثغرة BOLA المتعمدة ما زالت موجودة** في `fromAccountId`، فلا تعتمد على اسم المستخدم كآلية حماية.
