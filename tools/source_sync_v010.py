from pathlib import Path

path = Path('app/src/main/java/app/mdreader/mobile/MainActivity.java')
s = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'Expected source block not found: {label}')
    s = s.replace(old, new, 1)


replace_once(
    'format("❝",v->prefix("> ",false));format("•",v->prefix("- ",false));format("1.",v->prefix("",true));format("☑",v->prefix("- [ ] ",false));divider();',
    'format("❝",v->prefix("> ",false));format("•",v->prefix("- ",false));format("1.",v->prefix("",true));format("☑",v->setTaskState(false));divider();',
    'quick task toolbar action',
)

old_about = 'private void about(){message("MD Reader 0.9.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، تنزيل ملفات Markdown العامة مباشرة من GitHub، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.\\n\\nلا إعلانات • لا تحليلات • لا تتبع\\n\\nمفتاح API الشخصي يُحفظ مشفرًا على الجهاز.");}'
new_about = 'private void about(){message("MD Reader 0.10.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، مربعات الاختيار والإجابات الصحيحة المظللة، أدوات مباشرة لتحديد الإجابة الصحيحة وتوحيد مربعات الاختيار، معاينة محسنة للملفات الكبيرة، تنزيل ملفات Markdown العامة مباشرة من GitHub، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.\\n\\nلا إعلانات • لا تحليلات • لا تتبع\\n\\nمفتاح API الشخصي يُحفظ مشفرًا على الجهاز.");}'
replace_once(old_about, new_about, 'about version and features')

path.write_text(s)
print('v0.10 MainActivity final polish applied.')
