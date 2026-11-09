# VulnBank LAB — Vulnerable / Secure Comparison + Transaction Replay

## ملاحظة أمنية أساسية

اختيار Secure هنا يعني **نسخة تدريبية محصّنة نسبيًا في منطق الخادم**، ولا يعني أنها آمنة 100% أو صالحة للبنوك الحقيقية. تظل الاتصالات عبر HTTP محلي غير مشفّر، والكلمات التجريبية معروفة، وهناك حاجة لتدقيق أمني مستقل، TLS حقيقي، إدارة أسرار، مراقبة وقيود تشغيل قبل أي نشر حقيقي. **لا تفتح المنافذ على الشبكة العامة**.

## ملفات التعديل

- `ModeSelectionScreen.kt`: شاشة عند كل تشغيل: برتقالي Vulnerable، أخضر Secure.
- `BankApi.kt`: اختيار أحد خادمين ورفض الاتصال إذا كان الطرف الخاطئ يعمل؛ Burp للوضع الضعيف فقط.
- `VulnBankApp.kt`: وسوم الوضع وواجهة Replay Lab وإظهار دعم العملاء كنص غير تنفيذي في الوضع المحصّن.
- `Main.kt`: يختار المسارات من بيئة `LAB_MODE` التي يحددها Docker، **وليس من ترويسة يرسلها المهاجم**.
- `SecureRoutes.kt`: استعلامات محضّرة، كلمات مرور مجزأة، جلسات مصادقة مؤقتة، حدود محاولات الدخول، فحص ملكية الحسابات، تقييد الدعم، ومعالجة Idempotency داخل معاملة قاعدة البيانات.
- `db/init_secure.sql`: مستخدمون وحسابات تجريبية مماثلة للبداية ولكن في قاعدة مستقلة، وكلمات المرور فيها مخزّنة بتجزئة bcrypt عبر pgcrypto.
- `compose.yaml`: الخدمتان الأصليتان `db` و`server` كما كانتا، بالإضافة إلى `db_secure` و`server_secure` مع Volume مستقل `dbdata_secure` وشبكة Docker منفصلة `securenet`.

## التثبيت

1. احتفظ بنسخة احتياطية لمجلد مشروعك، و**فك ضغط Patch في جذر** `D:\Mobile Project\test\VulnBankLab` واستبدل الملفات المتطابقة فقط.
2. لا تحذف مجلد `db` ولا Docker Volume `dbdata`. **لا تستخدم** `docker compose down -v`.
3. تأكد أن Burp HTTP Listener على `127.0.0.1:8081` بدون Redirect، وأن Android Emulator global HTTP proxy معطّل (`adb shell settings put global http_proxy :0`) لأن التطبيق نفسه يحدد Burp للوضع الضعيف.
4. من PowerShell:

```powershell
cd "D:\Mobile Project\test\VulnBankLab"
docker compose config -q
docker compose up -d --build --force-recreate
docker compose ps
python .\scripts\verify_dual_mode.py
```

5. افتح **نفس المجلد** في Android Studio، Sync، ثم Run. لا يلزم تغيير Android SDK أو إضافة شهادات/Frida.
6. أول شاشة **Choose Your Security Environment**؛ اضغط **Vulnerable** أو **Secure**، وسيتحقق التطبيق أن الخادم المختار بالفعل في الوضع المطلوب.
7. استخدم `alice / password123` في **كلتا البيئتين**. **الرصيد الأولي نفسه، لكن التحويلات منفصلة** بين قاعدة البيانات القديمة والجديدة.

## المنافذ

| الوظيفة | Windows / Android Emulator |
| --- | --- |
| Vulnerable API | `127.0.0.1:8080` عبر Burp `10.0.2.2:8081` |
| Secure comparison API | `127.0.0.1:8082` على Windows، مباشرة من Android `10.0.2.2:8082` |
| Vulnerable PostgreSQL | `127.0.0.1:5433` |
| Secure PostgreSQL | منفذ داخلي فقط في Docker، دون port نشر على Windows |

## اختبار Transaction Replay من واجهة الهاتف

بعد تسجيل الدخول إلى أحد الوضعين:

1. افتح **API Lab**.
2. اضغط **Transaction Replay & Idempotency Lab**.
3. اضغط **Run Replay Test**؛ يرسل التطبيق طلبَي POST لتحويل **5 JOD وهمية** من حساب Alice إلى أول مستفيد آخر، وبنفس `Idempotency-Key` ونفس الحمولة.
4. قارن **Transfer IDs** و**Actual debit**.

| Vulnerable | Secure |
| --- | --- |
| `Idempotency-Key` موجود لكن يتم تجاهله | المفتاح إلزامي ويتم حفظه في PostgreSQL |
| تحويلان مختلفان، وخصم 10 JOD | تحويل واحد، وخصم 5 JOD فقط |
| إعادة إرسال الطلب تنتج تحويلًا ثانيًا | نفس المفتاح ونفس البيانات يعيد نفس transferId |
| تغيير `fromAccountId` قد يؤدي لخصم حساب آخر | يتحقق الخادم من ملكية المصدر للحساب المسجل |
| SQLi وStored XSS وAPI key التقليدية لا تزال متاحة للتدريب | مصادقة وSQL محضّر وعرض دعم كنص غير تنفيذي |

اختبار API إضافي:

```powershell
python .\scripts\verify_dual_mode.py --run-replay
```

للتحقق من الإعادة **المتزامنة** على الوضع المحصّن:

```powershell
python .\scripts\verify_dual_mode.py --run-concurrent
```

**هذه الاختبارات تغيّر الأرصدة التجريبية**. الفحص الافتراضي بدون أعلام لا ينفذ تحويلات.

## كيف يحقق Secure Idempotency؟

المفتاح مرتبط بالمستخدم المصادق، مع بصمة SHA-256 من `fromAccountId`, `toAccountId`, `amount`, `note`. قاعدة البيانات تفرض قيدًا فريدًا `(user_id, idempotency_key)`، وتخزن الـ `transfer_id` ضمن **نفس معاملة PostgreSQL التي تغيّر الأرصدة وتضيف السجلات**. الطلب المكرر ينتظر عند القيد الفريد إلى أن يكتمل الأول؛ إن كانت البيانات نفسها يحصل على رقم العملية الأصلية، وإن تغيرت يتم رفض إعادة استخدام المفتاح بـ `409 Conflict`.

## مقارنات إضافية

- Vulnerable: الاستعلام المبني نصيًا يسمح بمحاولة SQLi `' OR 1=1 --`؛ Secure: Prepared Statement + bcrypt.
- Vulnerable: API key مضمّن في APK؛ Secure: Bearer session من login ويتطلب تفويضًا خادميًا.
- Vulnerable: أي حساب ID ممكن للقراءة والتحويل؛ Secure: حسابك فقط للقراءة ومصدر التحويل يجب أن تملكه.
- Vulnerable: WebView ينفذ HTML/JS داخل تذاكر الدعم؛ Secure: عرض `Text` ولا تُنفذ JavaScript.
- Vulnerable: مصدر بيانات البنك القديم؛ Secure: قاعدة مستقلة، حتى لا يؤدي استغلال الوضع الضعيف للوصول لبيانات الوضع المحصّن.

## لا تتلف مشروع GitHub

يوصى باستخدام **ملف Patch فقط**، لأنه لا يستبدل `README.md` أو الأيقونة التي أضفتها أو صور GitHub. أضف رابط `docs/DUAL_MODE_REPLAY_AR.md` يدويًا إلى README بعد التحقق من البناء.
