# MD Reader — Progress Log

> سجل تقدم مختصر ومكمل لـ `PROJECT_MEMORY.md`. ذاكرة المشروع تبقى مصدر الحقيقة للقرارات والحالة المعتمدة.

## 2026-09-25 — v0.12.0 named code block titles + editor shortcut

- العمل على الفرع: `feature/code-block-titles`.
- PR: #7 (Draft).
- دعم عناوين fenced code بصيغة `title="..."` والصيغة المختصرة `language:title`.
- دعم أسماء عربية وإنجليزية مع syntax highlighting اختياري.
- إضافة header مرئي متناسق مع الوضعين الفاتح والداكن وزر النسخ الحالي.
- إضافة parser مستقل لبيانات code fence مع تعقيم للعنوان واللغة ودعم escaped quotes.
- إضافة اختصار **▣ حاوية بعنوان…** داخل **أدوات Markdown وHTML** بنفس تنسيق الأدوات الموجودة.
- الاختصار يلف النص المحدد أو يدرج placeholder جاهزًا عند عدم وجود تحديد.
- إضافة `MarkdownTransforms.titledFencedCode` مع fence ديناميكي للمحتوى الذي يحتوي backticks.
- تحديث اختبارات Java وJavaScript وAssertions الخاصة بـ CI.
- تحديث `README.md` و`PROJECT_MEMORY.md`.
- الحالة: التنفيذ مكتمل؛ انتظار نجاح CI النهائي ثم اختبار APK موقّع على الجهاز قبل أي دمج إلى `main`.
