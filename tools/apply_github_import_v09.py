from pathlib import Path


MAIN = Path("app/src/main/java/app/mdreader/mobile/MainActivity.java")
BUILD = Path("app/build.gradle")
WORKFLOW = Path(".github/workflows/build-apk.yml")
MEMORY = Path("PROJECT_MEMORY.md")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def update_main_activity() -> None:
    s = MAIN.read_text()

    old_home = '        LinearLayout row=new LinearLayout(this);TextView o=homeButton("فتح ملف",true),n=homeButton("ملف جديد",false);o.setOnClickListener(v->openPicker());n.setOnClickListener(v->newFile());row.addView(o,weight(1,0,4));row.addView(n,weight(1,4,0));homeContent.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));'
    new_home = old_home + '\n        TextView github=homeButton("تنزيل ملف من GitHub",false);github.setOnClickListener(v->githubImport());LinearLayout.LayoutParams githubLp=new LinearLayout.LayoutParams(-1,dp(50));githubLp.topMargin=dp(8);homeContent.addView(github,githubLp);'
    s = replace_once(s, old_home, new_home, "home GitHub button")

    open_anchor = '    private void openPicker(){confirm(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/markdown","text/x-markdown","text/plain","application/octet-stream"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_OPEN);});}'
    github_methods = '''    private void githubImport(){confirm(this::githubImportDialog);}\n\n    private void githubImportDialog(){\n        Dialog d=dialog();LinearLayout p=panel("تنزيل Markdown من GitHub");\n        p.addView(label("ألصق رابط ملف Markdown من GitHub. يدعم روابط github.com التي تحتوي /blob/ وروابط raw.githubusercontent.com العامة.",13,muted,false),textLp());\n        EditText link=multiLineField("https://github.com/user/repo/blob/main/file.md","",1);link.setMinLines(2);link.setMaxLines(4);link.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_MULTI_LINE);p.addView(link,textLp());\n        TextView status=label("مثال: https://github.com/ahmed9461/GitDock/blob/main/CHANGELOG.md",12,muted,false);status.setTextIsSelectable(true);p.addView(status,textLp());\n        TextView open=sheetButton("تنزيل وفتح",false),save=sheetButton("تنزيل وحفظ في الجهاز",false),close=sheetButton("إلغاء",false);\n        Runnable reset=()->{open.setEnabled(true);save.setEnabled(true);open.setAlpha(1f);save.setAlpha(1f);};\n        View.OnClickListener start=v->{boolean saveAfter=v==save;String value=link.getText().toString().trim();try{GitHubMarkdownSource.parse(value);}catch(Exception e){link.setError("استخدم رابط ملف Markdown مباشرًا من GitHub");return;}open.setEnabled(false);save.setEnabled(false);open.setAlpha(.55f);save.setAlpha(.55f);status.setText("جاري التنزيل…");io.execute(()->{try{GitHubMarkdownSource.DownloadedFile file=GitHubMarkdownSource.download(value);main.post(()->{if(!d.isShowing())return;status.setText("تم تنزيل "+file.fileName);dismissSheet(d,p,()->openGitHubDownload(file,saveAfter));});}catch(Exception e){main.post(()->{if(!d.isShowing())return;status.setText("تعذر التنزيل: "+GitHubMarkdownSource.userMessage(e));reset.run();});}});};\n        open.setOnClickListener(start);save.setOnClickListener(start);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(open,buttonLp());p.addView(save,buttonLp());p.addView(close,buttonLp());\n        showDialog(d,p,false);link.requestFocus();keyboard(link);\n    }\n\n    private void openGitHubDownload(GitHubMarkdownSource.DownloadedFile file,boolean saveAfter){\n        saveReadingPosition();currentUri=null;currentName=ensureMd(file.fileName);setText(file.text,true);dirty=false;drafts.clear();showDocument();setEditing(false);Toast.makeText(this,"تم تنزيل "+currentName,Toast.LENGTH_SHORT).show();if(saveAfter)saveAs();\n    }\n\n'''
    s = replace_once(s, open_anchor, github_methods + open_anchor, "GitHub import methods")

    old_more = 'private void more(){List<Action>a=new ArrayList<>();a.add(new Action("ملف جديد",this::newFile,false));'
    new_more = 'private void more(){List<Action>a=new ArrayList<>();a.add(new Action("ملف جديد",this::newFile,false));a.add(new Action("تنزيل من GitHub",this::githubImport,false));'
    s = replace_once(s, old_more, new_more, "overflow GitHub action")

    old_about = 'private void about(){message("MD Reader 0.8.1","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.'
    new_about = 'private void about(){message("MD Reader 0.9.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، تنزيل ملفات Markdown العامة مباشرة من GitHub، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.'
    s = replace_once(s, old_about, new_about, "about version")

    MAIN.write_text(s)


def update_build_gradle() -> None:
    g = BUILD.read_text()
    g = replace_once(g, "versionCode 11", "versionCode 12", "versionCode")
    g = replace_once(g, "versionName '0.8.1'", "versionName '0.9.0'", "versionName")
    BUILD.write_text(g)


def update_release_workflow() -> None:
    w = WORKFLOW.read_text()
    w = replace_once(w, "name: Build MD Reader v0.8.1 Release", "name: Build MD Reader v0.9.0 Release", "workflow name")
    w = replace_once(w, "- name: Assert direct-source architecture and v0.8.1 fixes", "- name: Assert direct-source architecture and v0.9 features", "workflow assertion label")
    w = replace_once(
        w,
        "          test -s app/src/main/java/app/mdreader/mobile/FlingEditText.java\n",
        "          test -s app/src/main/java/app/mdreader/mobile/FlingEditText.java\n          test -s app/src/main/java/app/mdreader/mobile/GitHubMarkdownSource.java\n",
        "workflow helper assertion",
    )
    w = replace_once(
        w,
        "          grep -q \"versionName '0.8.1'\" app/build.gradle\n          grep -q \"versionCode 11\" app/build.gradle\n",
        "          grep -q \"versionName '0.9.0'\" app/build.gradle\n          grep -q \"versionCode 12\" app/build.gradle\n          grep -q 'githubImportDialog' app/src/main/java/app/mdreader/mobile/MainActivity.java\n          grep -q 'GitHubMarkdownSource.download' app/src/main/java/app/mdreader/mobile/MainActivity.java\n",
        "workflow version assertions",
    )
    w = replace_once(
        w,
        "            app/src/main/java/app/mdreader/mobile/SearchReplaceEngine.java \\\n            tools/TransformSmokeTest.java \\",
        "            app/src/main/java/app/mdreader/mobile/SearchReplaceEngine.java \\\n            app/src/main/java/app/mdreader/mobile/GitHubMarkdownSource.java \\\n            tools/TransformSmokeTest.java \\",
        "workflow Java sources",
    )
    w = replace_once(
        w,
        "            tools/SearchReplaceSmokeTest.java\n",
        "            tools/SearchReplaceSmokeTest.java \\\n            tools/GitHubMarkdownSourceSmokeTest.java\n",
        "workflow smoke source",
    )
    w = replace_once(
        w,
        "          java -cp build/smoke app.mdreader.mobile.SearchReplaceSmokeTest\n",
        "          java -cp build/smoke app.mdreader.mobile.SearchReplaceSmokeTest\n          java -cp build/smoke app.mdreader.mobile.GitHubMarkdownSourceSmokeTest\n",
        "workflow smoke execution",
    )
    if "MD-Reader-v0.8.1" not in w:
        raise SystemExit("workflow output version anchor missing")
    w = w.replace("MD-Reader-v0.8.1", "MD-Reader-v0.9.0")
    w = replace_once(w, 'echo "version=0.8.1"', 'echo "version=0.9.0"', "release-info version")
    w = replace_once(w, 'echo "versionCode=11"', 'echo "versionCode=12"', "release-info versionCode")
    WORKFLOW.write_text(w)


def update_memory() -> None:
    m = MEMORY.read_text()
    heading = "## v0.9.0 — GitHub Markdown import (in development)"
    if heading not in m:
        m += """

## v0.9.0 — GitHub Markdown import (in development)
- Branch: `github-import-v0.9`.
- Adds direct import of public Markdown files from GitHub by pasting a `github.com/.../blob/.../*.md` or `raw.githubusercontent.com/.../*.md` URL.
- Home screen and overflow menu expose `تنزيل من GitHub`.
- User can either download/open in MD Reader or download then save to device with Android's document picker.
- Network fetch is HTTPS-only, follows only GitHub/raw GitHub redirects, uses explicit connect/read timeouts, and does not send credentials. Private repositories are intentionally not supported in this version.
- URL parsing has pure-Java smoke coverage including `https://github.com/ahmed9461/GitDock/blob/main/CHANGELOG.md`.
"""
    MEMORY.write_text(m)


update_main_activity()
update_build_gradle()
update_release_workflow()
update_memory()
print("v0.9 GitHub import transformation complete")
