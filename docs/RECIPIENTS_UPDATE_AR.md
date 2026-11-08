# تحديث مستفيدي التحويل — اسم المستخدم مع رقم الحساب

الواجهة الجديدة لا تطلب كتابة ID وحده: افتح **Transfer Money** وابحث عن `bob` أو `@bob` أو `2`، ثم اختر النتيجة:

**Bob Smith · @bob · Account #2**

يظهر حساب المرسِل أيضًا: **Alice Jordan · @alice · Account #1**.

## آلية العمل

- `GET /api/v1/recipients`: يجلب أسماء المستخدمين والـ @username وأرقام الحسابات من PostgreSQL.
- صفحة التحويل تعرض نتائج بحث حقيقية وتطلب منك اختيار المستفيد.
- `POST /api/v1/transfers`: يرسل الحقول `fromAccountId`, `fromUsername`, `toAccountId`, `toUsername`, `amount`, `note`.
- الاستجابة تعرض الاسم الحقيقي المحفوظ في قاعدة البيانات ورقم الحساب، بدل الاعتماد على أسماء قادمة من المستخدم.
- كشف `fromUsername`/`toUsername` هو للقراءة فقط، وليس وسيلة توثيق أو تفويض. ثغرة BOLA في `fromAccountId` ما زالت متعمدة للمختبر المحلي.

## تثبيت Patch على مشروع Windows الحالي

انسخ ملفات `VulnBank_Recipients_Names_Patch.zip` إلى جذر مشروعك، واستبدل الملفات فقط بعد الاحتفاظ بنسخة احتياطية. لا تحذف مجلد المشروع ولا بيانات PostgreSQL.

```powershell
cd 'D:\Mobile Project\VulnBankLab'
docker compose up -d --build
python scripts\test_recipients_api.py
```

في Android Studio اختر **Sync Project with Gradle Files** ثم **Run app**. إذا أردت تثبيت APK أحدث يدويًا:

```powershell
adb install -r -t '.\app\build\intermediates\apk\debug\app-debug.apk'
```

Burp Listener كما كان `127.0.0.1:8081`، بدون Frida أو شهادة. HTTP المحلي عبر Windows فقط. لا تستخدم هذا المشروع أو البيانات التجريبية في خدمة عامة.

## فحص الطلب في Burp

```json
{
  "fromAccountId": 1,
  "fromUsername": "alice",
  "toAccountId": 2,
  "toUsername": "bob",
  "amount": 15.00,
  "note": "Demo transfer"
}
```

عند اختبار التفويض ضمن المختبر فقط، غيّر `fromAccountId` في Repeater إلى `2` مع إبقاء `toAccountId=1` لمشاهدة أثر ثغرة BOLA. تمثّل جميع الأرصدة والمستخدمين أرقامًا وهمية.
