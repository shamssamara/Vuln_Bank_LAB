> **إعداد جديد بدون Frida أو شهادة:** اتبع `docs/BURP_NO_CERT_AR.md`. التطبيق الآن يستخدم HTTP على 8080 وProxy على 8081، وليس HTTPS/SSL Pinning. تفاصيل HTTPS/Frida أدناه تخص الإصدار الأصلي فقط.

# ابدأ من هنا — VulnBank LAB

> تحذير: هذا المشروع يحتوي على ثغرات مقصودة. للاستعمال المحلي فقط، والبيانات البنكية وجميع المستخدمين وهميون.

## المرحلة الأولى: تشغيل الخادم على Windows

1. ثبّت **Docker Desktop** وتأكد أنه يعمل بـ Linux Containers.
2. فك ضغط المشروع في مسار بسيط مثل `C:\Lab\VulnBankLab`.
3. افتح PowerShell داخل المجلد الرئيسي وشغّل:

```powershell
docker compose up -d --build
docker compose ps
python scripts\smoke_test.py
```

يفترض أن الاختبار يمر بنجاح. يعمل الخادم محليًا على `https://localhost:8443`، وقاعدة PostgreSQL على `127.0.0.1:5432` فقط. شهادة TLS مصممة للمختبر وموجودة ضمن الملفات.

## المرحلة الثانية: Android Studio

1. افتح مجلد `VulnBankLab` كاملًا من Android Studio.
2. وافق على تنزيل Gradle والـ Android SDK، وثبّت **SDK 36** إذا لزم.
3. شغّل Android Emulator على نفس جهاز Windows الذي يشغّل Docker.
4. شغّل التطبيق من الإعداد `app`.
5. حساب تجريبي: `alice` وكلمة المرور `password123`.

الـ Emulator يتواصل مع Windows عبر `https://10.0.2.2:8443`، وليس `localhost` الخاص به.

## المرحلة الثالثة: تجارب الاختراق

| التجربة | ما تفعله |
|---|---|
| SQL injection | ضع `' OR 1=1 --` في حقل Username وكلمة مرور عشوائية |
| Stored XSS | اكتب `<script>alert("XSS");</script>` في Support، أرسلها، وافتح التذكرة |
| API key | حلّل APK بـ JADX وابحث عن `LabSecrets` |
| API & BOLA | من API Lab جرّب Customer IDs وAccount IDs مختلفة |
| Frida SSL | شغّل سكربت `frida_scripts/ssl_pinning_bypass.js` على محاكي الاختبار |
| Frida root | فعّل Simulate rooted emulator ثم نفّذ سكربت root bypass |
| Frida auth | اضغط Test isAuthenticated قبل وبعد تشغيل سكربت auth bypass |
| Frida secret | شغّل سكربت runtime key وراقب الطرفية عند استدعاء Getter |

### تشغيل جميع سكربتات Frida دفعة واحدة

```bash
frida -U -f com.vulnbank.lab -l frida_scripts/all_lab_hooks.js
```

**ملاحظة:** يجب أن يتصل Frida بالمحاكي أولًا (`frida-ps -U`). قد تحتاج Frida Server متوافقًا مع معمارية المحاكي. إن كنت على Kali VM، فالمحاكي الموجود على Windows ليس ظاهرًا تلقائيًا في Kali.

### سكربت اختبار الخادم

```powershell
python scripts\smoke_test.py
```

### إعادة بيانات المختبر إلى البداية

```powershell
.\scripts\reset_lab.ps1
```

هذه العملية تحذف بيانات قاعدة المختبر الوهمية وتعيد إنشاءها. لا تستخدمها إن كنت تريد الاحتفاظ بتذاكر الدعم التجريبية.

## لماذا Docker على Windows وKali للتحليل؟

Docker والخادم والمحاكي جميعًا على Windows = أقل مشاكل شبكات وADB، ويمكن استخدام Kali بعد ذلك للتحليل مع Burp/JADX/Frida إذا تم إعداد الاتصال بشكل صحيح.

لمزيد من المعلومات افتح `README.md` و `docs/LAB_EXERCISES.md` و `docs/API_REFERENCE.md`.
