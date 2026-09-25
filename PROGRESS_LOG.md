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
- GitHub Actions Run `36168040146`: **Success** بالكامل، بما في ذلك الاختبارات وبناء APK/AAB والتحقق ورفع Artifact.
- تم إنشاء APK موقّع للاختبار من Artifact الخاص بـ Run `36168109617` باستخدام مفتاح Release الدائم نفسه.
- SHA-256 للنسخة الموقعة: `d25d7a312d3449bb5c9853e956e88cebda6a806aa2807be70d960f63db9d9318`.
- شهادة التوقيع SHA-256: `3d7f963db1c9b5211d3a7f0c227d208108474b390802afa6891133a035bf835a`، مطابقة للإصدارات السابقة، مع APK Signature Scheme v2 + v3.
- الحالة: التنفيذ وCI والتوقيع مكتملة؛ المتبقي اختبار الجهاز وموافقة المستخدم قبل أي دمج إلى `main`.
