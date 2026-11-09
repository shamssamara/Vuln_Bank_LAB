# تنزيل مشروع VulnBank الكامل

هذا الأرشيف هو المشروع الكامل وليس Patch، لذا يمكنك فكّه في **مجلد جديد** بدل استبدال ملفات مشروع موجود.

ابدأ من [`START_HERE_AR.md`](START_HERE_AR.md) ثم اقرأ [`DUAL_MODE_REPLAY_AR.md`](DUAL_MODE_REPLAY_AR.md).

للحفاظ على بيانات PostgreSQL الموجودة في جهازك: لا تستخدم `docker compose down -v` ولا تحذف Docker volumes. لاحظ أن `compose.yaml` يحدد اسم المشروع `vulnbanklab`؛ تشغيل نسختين من مسارين مختلفين بنفس الاسم يُعيد استخدام خدمات وVolumes المشروع نفسها. لذلك لا تشغّل النسختين جنبًا إلى جنب بهذه الإعدادات.
