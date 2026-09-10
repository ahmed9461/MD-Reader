from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/app/mdreader/mobile/MainActivity.java"
text = path.read_text(encoding="utf-8")

replacements = [
    (
        r'''                new Action("▦ جدول",()->insertMarkdownBlock("| العمود 1 | العمود 2 |\\n| --- | --- |\\n| قيمة | قيمة |"),false),''',
        r'''                new Action("▦ جدول",()->insertMarkdownBlock("| العمود 1 | العمود 2 |"+System.lineSeparator()+"| --- | --- |"+System.lineSeparator()+"| قيمة | قيمة |"),false),''',
    ),
    (
        r'''        String before=a>0&&e.charAt(a-1)!='\n'?"\\n\\n":"";''',
        r'''        String before=a>0&&e.charAt(a-1)!='\n'?System.lineSeparator()+System.lineSeparator():"";''',
    ),
    (
        r'''        String after=b<e.length()&&e.charAt(b)!='\n'?"\\n\\n":"";''',
        r'''        String after=b<e.length()&&e.charAt(b)!='\n'?System.lineSeparator()+System.lineSeparator():"";''',
    ),
    (
        r'''        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);''',
        r'''        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);
        android.widget.Scroller editorScroller=new android.widget.Scroller(this);
        editorScroller.setFriction(android.view.ViewConfiguration.getScrollFriction()*1.35f);
        editor.setScroller(editorScroller);''',
    ),
]

for old, new in replacements:
    if text.count(old) != 1:
        raise SystemExit(f"Expected one match, found {text.count(old)} for: {old[:80]}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Fixed v0.7 Markdown block newlines and editor scroll friction")
