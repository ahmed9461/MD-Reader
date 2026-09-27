# MD Reader — Progress Log

> سجل تقدم مكمل لـ `PROJECT_MEMORY.md`. تقرير `docs/DOCUMENTS_SPACING_VALIDATION.md` يحمل بيانات تسليم v0.13.1، وتقرير `docs/UI_REDESIGN_VALIDATION.md` يحفظ بيانات v0.13.0 السابقة وحدود الفحص. الإدخالات المؤرخة أدناه تاريخية؛ الأحدث يحدد حالة الاعتماد.

## 2026-09-27 — Owner accepts v0.13.1 and explicitly authorizes merge

- After delivery of the signed spacing update, the owner said: **«كفو تم ادمج🫡❤»**. The v0.13.1 acceptance gate is satisfied; this is distinct from the earlier 9.5/10 feedback on v0.13.0.
- PR #9 targets `main` from `feature/ui-ux-refresh`; it was open, draft and mergeable when integration started. Converted it to ready after explicit approval. PR #9's final state records the actual merge result.
- Reviewed current PR metadata, reviews, source comparison and Actions. `187cf54` to `1ddf0fc` changes documentation only; the accepted application source and signed APK remain unchanged.
- Release run `36274012848` on `1ddf0fc51b419f873ed78e398dd9847022250d3d` succeeded. UI run `36274012837` initially failed at `landscape leaves document viewport`; re-ran the failed job rather than claiming an older successful run covered current HEAD.
- Downloaded the failed-run evidence artifact `10916771418` and inspected `failure.png`. It shows the intended side-by-side landscape layout after the screenshot's additional pause; the test uses a fixed 1600ms rotation wait. Timing instability remains a possible explanation, not a demonstrated production fix.
- Updated README, project memory and the existing plan to record the owner's acceptance, remove obsolete candidate status from current sections, preserve historical delivery evidence and require future delivered versionCode > 17.
- No production source, test assertion, package, signing key, file data, feature or delivered APK was changed in this adoption follow-up. No unrelated branch or artifact was deleted.
- Final integration must use successful release and UI checks for the final relevant HEAD; record exact run IDs and merge SHA in PR #9. Do not bypass a failure merely because the owner approved merge.
- The accepted v0.13.1 APK remains the same installed update. Documentation-only integration does not require a new APK or reinstallation. Owner acceptance does not imply every Android/font/keyboard/service combination was individually tested.

## 2026-09-26 — v0.13.1 Documents spacing follow-up ready for phone testing

- Owner likes v0.13.0 (9.5/10), but supplied a phone screenshot showing no separation between the open-file button and first document card.
- Root cause: the action layout has only a top margin, and the card layout only a bottom margin. Added a local 16dp bottom margin to the Documents action; no shared spacing, file data, navigation, icons, package or signing-key change.
- Added light/dark device checks for the actual 16dp boundary, unchanged 9dp inter-card spacing, aligned edges and 48dp button height.
- Candidate version 0.13.1 / 17; tested source `187cf54d65a007349e185830f1af44bf283d6242`.
- Release run `36273649564` succeeded through APK/AAB verification and artifact upload (`10916531389`). Device run `36273649560` succeeded with **44 assertions** and UI artifact `10915574979`.
- Captured 14 real emulator screenshots; opened and visually reviewed the light/dark Documents screenshots for this focused fix.
- Signed with the permanent key; v2/v3, same certificate as the prior v0.13.0 APK, packaged identity 0.13.1 / 17, ARM64 and ZIP integrity verified. Signed size: 18,228,538 bytes.
- Signed SHA-256: `b126ebfc1fbdecc50fdb5ddb5f2b52fa426612b4e60b5d5857e45e8f8d6220c2`. Full provenance and limits: `docs/DOCUMENTS_SPACING_VALIDATION.md`.
- Temporary source transport was removed before the tested source commit; final builds compile direct sources. Documentation updates do not change the signed APK.
- Same draft PR #9 and existing plan. main unchanged; no merge approval. Updated physical-phone test remains pending.

## 2026-09-26 — v0.13.0 UI / UX candidate ready for phone testing

- main بقي v0.12.0 / code 15 عند `683c5c93ffe4674169039391e985bb9de2b24299` دون تعديل.
- الفرع `feature/ui-ux-refresh`، الخطة `plans/0013-ui-ux-refresh.md`، Draft PR #9. **لا اعتماد أو دمج حتى تجربة الهاتف وموافقة المستخدم.**
- تجديد الرئيسية والمستندات والمفضلة والإعدادات وبطاقات الملفات وشريط المستند وتبويبي القراءة والتحرير.
- ألوان وأيقونات وحالات وأهداف لمس موحدة، ولوحات قابلة للتمرير بعناوين وإغلاق ثابتين. الصوت والترجمة يستخدمان التنسيق نفسه.
- البحث والاستبدال داخل التخطيط دون تغطية النص، مع طي الاستبدال وتمييز النتائج وإظهارها والسابق/التالي دون سرقة تركيز البحث.
- اختبار المحاكي كشف تأخر IME البارد ثم مشكلة حقيقية في مساحة النص الأفقية؛ أُصلحت بالانتظار المحدود لظهور IME وبترتيب البحث بجانب المستند عند ضيق الارتفاع. امتدت حماية المساحة إلى الكتابة العادية، مع اختبارات إضافية دون تخفيف شروط النجاح.
- الأيقونة من صورة `.md` المقدمة من المستخدم، والمعاينة متناسقة معها. وظائف HTML الآمن وعناوين حاويات الكود والحفظ والمسودات والترجمة والصوت والأداء الكبيرة بقيت في المصدر.
- المصدر الذي بُني واختُبر للتسليم: `a5974fb6eae24d9adb6643f5bc9cce361118d6dd`.
- Release run `36269131631`: **Success**، smoke/JS/security/UI contracts وبناء ARM64 APK وAAB منفصل والتحقق ورفع artifact `10915175891`.
- UI run `36269131647`: **Success — 32 assertions**، artifact `10914594351`، و13 لقطة فعلية راجعت بصريًا.
- Java SearchMatchState: **277 checks passed**، مع نجاح اختبارات Java السابقة وJS وXML وdiff.
- APK موقّع بالتوقيع الدائم، حزمة `app.mdreader.mobile`، v0.13.0 / code 16، ARM64، حجم 18,228,538 بايت.
- SHA-256 للنسخة الموقعة: `75059342cc6a611dc8c6e36eaf1c46eb0a3d9f25e1e57e16503300bab4c06555`.
- شهادة SHA-256: `3d7f963db1c9b5211d3a7f0c227d208108474b390802afa6891133a035bf835a`، v2/v3 ناجحان؛ فُحصت هوية الحزمة من داخل APK الموقّع.
- تحديث الذاكرة والخطة وREADME وتقرير مراجعة الواجهات وتقرير التحقق، وحفظ ذاكرة v0.12.0 السابقة حرفيًا في docs/history.
- المصدر مباشر. ملفات نقل/patch مؤقتة خضعت لفحص hashes ثم أزيلت. فشل أول نقل بسبب صلاحية workflows؛ اقتصر Actions بعدها على مصدر التطبيق، وتغييرات workflow تمت عبر موصل GitHub المصرح. لا أسرار في المصدر أو ملفات النقل.
- أزيلت خطوة حذف artifacts السابقة غير المرتبطة؛ لم تُحذف إصدارات أخرى.
- التحديثات التوثيقية بعد المصدر المذكور لا تغير التطبيق المُسلّم. اختبار المحاكي لا يعادل تجربة ترقية الهاتف أو جميع الخدمات الخارجية وأحجام الخط؛ الحدود موثقة في التقرير.
- **الحالة وقت التسليم السابق: مرشح موقّع جاهز للتجربة، غير مدمج. وردت ملاحظات المستخدم وعولجت في متابعة v0.13.1 أعلاه.**

## 2026-09-25 — v0.12.0 named code block titles + editor shortcut

- التطوير تم على الفرع: `feature/code-block-titles`.
- PR: #7 — **Merged**.
- دعم عناوين fenced code بصيغة `title="..."` والصيغة المختصرة `language:title`.
- دعم أسماء عربية وإنجليزية مع syntax highlighting اختياري.
- إضافة header مرئي متناسق مع الوضعين الفاتح والداكن وزر النسخ الحالي.
- إضافة parser مستقل لبيانات code fence مع تعقيم للعنوان واللغة ودعم escaped quotes.
- إضافة اختصار **▣ حاوية بعنوان…** داخل **أدوات Markdown وHTML** بنفس تنسيق الأدوات الموجودة.
- الاختصار يلف النص المحدد أو يدرج placeholder جاهزًا عند عدم وجود تحديد.
- تحديث اختبارات Java وJavaScript وAssertions الخاصة بـ CI.
- تحديث `README.md` و`PROJECT_MEMORY.md`.
- GitHub Actions Run `36168040146`: **Success** بالكامل، بما في ذلك الاختبارات وبناء APK/AAB والتحقق ورفع Artifact.
- تم إنشاء APK موقّع للاختبار من Artifact الخاص بـ Run `36168109617` باستخدام مفتاح Release الدائم نفسه.
- SHA-256 للنسخة الموقعة: `d25d7a312d3449bb5c9853e956e88cebda6a806aa2807be70d960f63db9d9318`.
- شهادة التوقيع SHA-256: `3d7f963db1c9b5211d3a7f0c227d208108474b390802afa6891133a035bf835a`، مطابقة للإصدارات السابقة، مع APK Signature Scheme v2 + v3.
- اختبار الجهاز 2026-09-25: المستخدم أكد أن النسخة الموقعة تعمل بشكل ممتاز وأن اختصار **▣ حاوية بعنوان…** يعمل كما هو مطلوب.
- 2026-09-25: المستخدم أعطى موافقة صريحة على الدمج بعد نجاح اختبار الهاتف.
- PR #7 دُمج إلى `main` بطريقة squash في 2026-09-25.
- merge commit: `ae7940180eccfa0f3bcb6d1844a4da97ff13828d`.
- الحالة: **v0.12.0 Stable / معتمد على main** بعد CI ناجح، توقيع صحيح، اختبار هاتف ناجح، وموافقة المستخدم.
