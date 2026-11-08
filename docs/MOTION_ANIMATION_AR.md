# VulnBank — حركة التطبيق الجديدة (Jetpack Compose)

تمت إضافة ثلاث حركات خفيفة إلى التطبيق فقط:

1. عند فتح التطبيق: شاشة شعار VulnBank متحركة، حلقة دائرية متدرجة تدور، وتوقيع SHAMS SAMARA؛ تختفي تلقائيًا بعد نحو ثانية واحدة.
2. عند الانتقال بين Login وHome وTransfer وSupport وAPI Lab وMore: انزلاق جانبي خفيف وظهور تدريجي، مع شعار وحلقة تحميل قصيرة (حوالي 320 مللي ثانية).
3. عند إجراء طلب API مثل Refresh أو Login أو Send Money: يظهر الشعار والحلقة الدوارة حتى تعود الاستجابة فعليًا، بدون تأخير مصطنع.

## التثبيت في مشروعك الحالي (الموصى به)

افك ملف VulnBank_Motion_Only_Patch.zip داخل المجلد:
`D:\Mobile Project\test\VulnBankLab`
مع استبدال الملفات المطابقة فقط. لا تحتاج لتغيير Docker أو PostgreSQL أو Burp.

افتح المشروع في Android Studio ثم Sync Project with Gradle Files وبعدها Run لتثبيت الإصدار المحدّث.

أو في PowerShell، بعد البناء من Android Studio:
`adb install -r -t "D:\Mobile Project\test\VulnBankLab\app\build\intermediates\apk\debug\app-debug.apk"`

> ملاحظة: اسم هذا الملف والمسار قد يختلفان وفق إعدادات بناء Android Studio.

## الملفات المتغيرة

- app/src/main/java/com/vulnbank/lab/ui/VulnBankMotion.kt (جديد)
- app/src/main/java/com/vulnbank/lab/ui/VulnBankApp.kt (انتقال الصفحات وحالة التحميل)
- app/build.gradle.kts (اعتماد Compose Animation)

لا توجد تغييرات على API أو SQL Injection أو XSS أو Transfer أو Docker في حزمة التعديل فقط.
