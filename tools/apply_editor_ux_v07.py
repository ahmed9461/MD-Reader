from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def patch_reader():
    path = ROOT / "app/src/main/assets/reader.html"
    text = path.read_text(encoding="utf-8")
    text = replace_once(text, "    breaks:false,", "    breaks:true,", "markdown soft breaks")
    path.write_text(text, encoding="utf-8")


def patch_manifest():
    path = ROOT / "app/src/main/AndroidManifest.xml"
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '            android:launchMode="singleTop">',
        '            android:launchMode="singleTop"\n'
        '            android:windowSoftInputMode="adjustResize">',
        "IME resize mode",
    )
    path.write_text(text, encoding="utf-8")


def patch_reading_store():
    path = ROOT / "app/src/main/java/app/mdreader/mobile/ReadingStore.java"
    text = path.read_text(encoding="utf-8")

    marker = '''    private static final String PREF_POSITIONS = "reading_positions_v1";
    private static final String PREF_BOOKMARKS = "reading_bookmarks_v1";'''
    replacement = '''    static final class EditorState {
        final int selectionStart;
        final int selectionEnd;
        final int scrollY;

        EditorState(int selectionStart, int selectionEnd, int scrollY) {
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.scrollY = scrollY;
        }
    }

    private static final String PREF_POSITIONS = "reading_positions_v1";
    private static final String PREF_BOOKMARKS = "reading_bookmarks_v1";
    private static final String PREF_EDITOR_STATES = "editor_states_v1";'''
    text = replace_once(text, marker, replacement, "ReadingStore constants")

    marker = '''    synchronized List<Bookmark> bookmarks(String doc) {'''
    replacement = '''    synchronized EditorState editorState(String doc) {
        if (doc == null || doc.isEmpty()) return new EditorState(-1, -1, 0);
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject o = root.optJSONObject(doc);
            if (o == null) return new EditorState(-1, -1, 0);
            return new EditorState(
                    Math.max(-1, o.optInt("selectionStart", -1)),
                    Math.max(-1, o.optInt("selectionEnd", -1)),
                    Math.max(0, o.optInt("scrollY", 0)));
        } catch (Exception ignored) {
            return new EditorState(-1, -1, 0);
        }
    }

    synchronized void saveEditorState(String doc, int selectionStart, int selectionEnd, int scrollY) {
        if (doc == null || doc.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject o = new JSONObject()
                    .put("selectionStart", Math.max(0, selectionStart))
                    .put("selectionEnd", Math.max(0, selectionEnd))
                    .put("scrollY", Math.max(0, scrollY));
            root.put(doc, o);
            prefs.edit().putString(PREF_EDITOR_STATES, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    synchronized List<Bookmark> bookmarks(String doc) {'''
    text = replace_once(text, marker, replacement, "ReadingStore editor state methods")

    marker = '''            JSONObject bm = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = bm.optJSONArray(from);
            if (a != null) { bm.put(to, a); bm.remove(from); }
            prefs.edit().putString(PREF_POSITIONS, pos.toString()).putString(PREF_BOOKMARKS, bm.toString()).apply();'''
    replacement = '''            JSONObject bm = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = bm.optJSONArray(from);
            if (a != null) { bm.put(to, a); bm.remove(from); }
            JSONObject editor = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject state = editor.optJSONObject(from);
            if (state != null) { editor.put(to, state); editor.remove(from); }
            prefs.edit()
                    .putString(PREF_POSITIONS, pos.toString())
                    .putString(PREF_BOOKMARKS, bm.toString())
                    .putString(PREF_EDITOR_STATES, editor.toString())
                    .apply();'''
    text = replace_once(text, marker, replacement, "ReadingStore move state")

    path.write_text(text, encoding="utf-8")


def patch_main_activity():
    path = ROOT / "app/src/main/java/app/mdreader/mobile/MainActivity.java"
    text = path.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "    private TextView title,searchCount,previewTab,editTab,homeBtn,openBtn,saveBtn,findBtn,moreBtn;",
        "    private TextView title,searchCount,previewTab,editTab,homeBtn,openBtn,saveBtn,findBtn,moreBtn,quickNavBtn,extraToolsBtn;",
        "toolbar fields",
    )

    text = replace_once(
        text,
        "        buildUi(); applyTheme(); configureEditor(); configurePreview();",
        "        buildUi(); applyTheme(); configureEditor(); configureEditorComfort(); enhanceEditorToolbar(); configurePreview();",
        "editor UX init",
    )

    helpers = r'''
    private void configureEditorComfort(){
        editor.setScrollContainer(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(true);
        editor.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        editor.setOverScrollMode(View.OVER_SCROLL_NEVER);
        editor.setGravity(Gravity.TOP|Gravity.START);
        editor.setHorizontallyScrolling(false);
        if(Build.VERSION.SDK_INT>=23){
            editor.setScrollIndicators(View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM,
                    View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM);
        }
    }

    private void enhanceEditorToolbar(){
        if(formatInner==null)return;
        quickNavBtn=mini("⇅ تنقل",false);
        quickNavBtn.setContentDescription("التنقل السريع داخل المستند");
        quickNavBtn.setOnClickListener(v->documentNavigation());
        extraToolsBtn=mini("+ أدوات",false);
        extraToolsBtn.setContentDescription("أدوات Markdown إضافية");
        extraToolsBtn.setOnClickListener(v->extraMarkdownTools());
        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));
        navLp.setMarginEnd(dp(6));
        LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(42));
        toolsLp.setMarginEnd(dp(6));
        formatInner.addView(extraToolsBtn,0,toolsLp);
        formatInner.addView(quickNavBtn,0,navLp);
        styleEditorToolbarExtras();
    }

    private void styleEditorToolbarExtras(){
        if(quickNavBtn!=null){quickNavBtn.setTextColor(text);quickNavBtn.setBackground(round(bg,border,10));}
        if(extraToolsBtn!=null){extraToolsBtn.setTextColor(text);extraToolsBtn.setBackground(round(bg,border,10));}
    }

    private void saveEditorPosition(){
        String doc=currentDocKey();
        if(doc.isEmpty()||editor==null)return;
        int a=Math.max(0,editor.getSelectionStart()),b=Math.max(0,editor.getSelectionEnd());
        reading.saveEditorState(doc,a,b,Math.max(0,editor.getScrollY()));
    }

    private void restoreEditorPosition(){
        String doc=currentDocKey();
        if(doc.isEmpty()||editor==null)return;
        ReadingStore.EditorState st=reading.editorState(doc);
        int len=editor.length();
        int a=st.selectionStart>=0?Math.min(st.selectionStart,len):Math.round(reading.position(doc)*len);
        int b=st.selectionEnd>=0?Math.min(Math.max(a,st.selectionEnd),len):a;
        editor.setSelection(a,b);
        final int wantedScroll=Math.max(0,st.scrollY);
        editor.post(()->{
            if(!editing||!doc.equals(currentDocKey()))return;
            android.text.Layout layout=editor.getLayout();
            if(layout==null){editor.bringPointIntoView(a);return;}
            if(st.selectionStart<0){editor.bringPointIntoView(a);return;}
            int visible=Math.max(1,editor.getHeight()-editor.getTotalPaddingTop()-editor.getTotalPaddingBottom());
            int maxScroll=Math.max(0,layout.getHeight()-visible);
            editor.scrollTo(0,Math.min(wantedScroll,maxScroll));
            editor.bringPointIntoView(a);
        });
    }

    private void documentNavigation(){
        List<Action>a=new ArrayList<>();
        a.add(new Action("البداية",()->jumpToRatio(0f),false));
        a.add(new Action("25% من المستند",()->jumpToRatio(.25f),false));
        a.add(new Action("المنتصف",()->jumpToRatio(.5f),false));
        a.add(new Action("75% من المستند",()->jumpToRatio(.75f),false));
        a.add(new Action("النهاية",()->jumpToRatio(1f),false));
        a.add(new Action("الذهاب إلى رقم سطر…",this::lineJumpDialog,false));
        if(!OutlineParser.parse(txt()).isEmpty())a.add(new Action("الفهرس",this::outline,false));
        a.add(new Action("إلغاء",null,false));
        sheet("التنقل السريع",a.toArray(new Action[0]));
    }

    private void jumpToRatio(float ratio){
        float r=Math.max(0f,Math.min(1f,ratio));
        if(editing)jumpEditorOffset(Math.round(editor.length()*r));
        else scrollToRatio(r);
    }

    private void jumpEditorOffset(int offset){
        if(!editing)setEditing(true);
        int o=Math.max(0,Math.min(offset,editor.length()));
        editor.requestFocus();
        editor.setSelection(o);
        editor.post(()->{
            editor.bringPointIntoView(o);
            saveEditorPosition();
        });
    }

    private int logicalLineCount(){
        String s=txt();int lines=1;
        for(int i=0;i<s.length();i++)if(s.charAt(i)=='\n')lines++;
        return lines;
    }

    private int offsetForLogicalLine(int requested){
        String s=txt();int line=Math.max(1,Math.min(requested,logicalLineCount()));
        if(line<=1)return 0;
        int seen=1;
        for(int i=0;i<s.length();i++)if(s.charAt(i)=='\n'&&++seen==line)return i+1;
        return s.length();
    }

    private void lineJumpDialog(){
        if(!editing)setEditing(true);
        Dialog d=dialog();LinearLayout p=panel("الذهاب إلى سطر");
        int total=logicalLineCount();
        p.addView(label("عدد أسطر المستند: "+total,13,muted,false),textLp());
        EditText line=inputField("رقم السطر من 1 إلى "+total,"",false);
        line.setInputType(InputType.TYPE_CLASS_NUMBER);
        p.addView(line,textLp());
        TextView go=sheetButton("انتقال",false);
        TextView close=sheetButton("إلغاء",false);
        go.setOnClickListener(v->{
            int n;try{n=Integer.parseInt(line.getText().toString().trim());}catch(Exception e){n=1;}
            final int target=offsetForLogicalLine(n);
            dismissSheet(d,p,()->jumpEditorOffset(target));
        });
        close.setOnClickListener(v->dismissSheet(d,p,null));
        p.addView(go,buttonLp());p.addView(close,buttonLp());
        showDialog(d,p,false);
        line.requestFocus();
    }

    private void extraMarkdownTools(){
        sheet("أدوات Markdown إضافية",
                new Action("☑ قائمة مهام",this::insertTaskList,false),
                new Action("▦ جدول",()->insertMarkdownBlock("| العمود 1 | العمود 2 |\\n| --- | --- |\\n| قيمة | قيمة |"),false),
                new Action("— فاصل أفقي",()->insertMarkdownBlock("---"),false),
                new Action("إلغاء",null,false));
    }

    private void insertMarkdownBlock(String block){
        if(!editing)setEditing(true);
        Editable e=editor.getText();
        int a=Math.max(0,Math.min(editor.getSelectionStart(),editor.getSelectionEnd()));
        int b=Math.max(a,Math.max(editor.getSelectionStart(),editor.getSelectionEnd()));
        String before=a>0&&e.charAt(a-1)!='\n'?"\\n\\n":"";
        String after=b<e.length()&&e.charAt(b)!='\n'?"\\n\\n":"";
        String value=before+block+after;
        e.replace(a,b,value);
        int caret=Math.min(e.length(),a+before.length()+block.length());
        editor.setSelection(caret);
        editor.bringPointIntoView(caret);
    }

    private void insertTaskList(){
        if(!editing)setEditing(true);
        Editable e=editor.getText();
        int a=Math.max(0,Math.min(editor.getSelectionStart(),editor.getSelectionEnd()));
        int b=Math.max(a,Math.max(editor.getSelectionStart(),editor.getSelectionEnd()));
        String all=e.toString();
        int lineStart=all.lastIndexOf('\n',Math.max(0,a-1));lineStart=lineStart<0?0:lineStart+1;
        int lineEnd=all.indexOf('\n',b);if(lineEnd<0)lineEnd=all.length();
        String[] lines=all.substring(lineStart,lineEnd).split("\\n",-1);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            String line=lines[i];
            if(line.matches("^\\s*- \\[[ xX]\\] .*"))out.append(line);
            else out.append("- [ ] ").append(line);
        }
        e.replace(lineStart,lineEnd,out.toString());
        int caret=Math.min(e.length(),lineStart+out.length());
        editor.setSelection(caret);
        editor.bringPointIntoView(caret);
    }

'''
    text = replace_once(
        text,
        "    private void configurePreview(){",
        helpers + "    private void configurePreview(){",
        "editor UX helpers",
    )

    old = '''    private void setEditing(boolean on){if(homeMode)showDocument();if(on&&!editing)saveReadingPosition();editing=on;if(on){preview.setVisibility(View.GONE);editor.setVisibility(View.VISIBLE);formatBar.setVisibility(View.VISIBLE);}else{render();editor.setVisibility(View.GONE);formatBar.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);hideKeyboard();}clearSearch();styleTabs();}'''
    new = '''    private void setEditing(boolean on){if(homeMode)showDocument();if(on==editing){if(on)restoreEditorPosition();return;}if(on&&!editing)saveReadingPosition();if(!on&&editing)saveReadingPosition();editing=on;if(on){preview.setVisibility(View.GONE);editor.setVisibility(View.VISIBLE);formatBar.setVisibility(View.VISIBLE);restoreEditorPosition();}else{render();editor.setVisibility(View.GONE);formatBar.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);hideKeyboard();preview.postDelayed(this::restoreReadingPosition,220);}clearSearch();styleTabs();}'''
    text = replace_once(text, old, new, "setEditing")

    text = replace_once(
        text,
        'a.add(new Action("حفظ باسم",this::saveAs,false));a.add(new Action("الفهرس",this::outline,false));a.add(new Action("القراءة بالصوت"',
        'a.add(new Action("حفظ باسم",this::saveAs,false));a.add(new Action("الفهرس",this::outline,false));a.add(new Action("التنقل السريع",this::documentNavigation,false));a.add(new Action("القراءة بالصوت"',
        "more navigation action",
    )

    text = replace_once(
        text,
        '''    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean(PREF_DARK,dark).apply();applyTheme();}''',
        '''    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean(PREF_DARK,dark).apply();applyTheme();styleEditorToolbarExtras();}''',
        "toolbar theme refresh",
    )

    text = replace_once(
        text,
        '''if(editing){editor.requestFocus();editor.setSelection(Math.min(h.offset,editor.length()));}else''',
        '''if(editing){jumpEditorOffset(Math.min(h.offset,editor.length()));}else''',
        "outline editor jump",
    )

    old = '''if(editing){int len=Math.max(1,editor.length());reading.savePosition(doc,Math.max(0f,Math.min(1f,(float)editor.getSelectionStart()/len)));return;}'''
    new = '''if(editing){saveEditorPosition();int len=Math.max(1,editor.length());reading.savePosition(doc,Math.max(0f,Math.min(1f,(float)editor.getSelectionStart()/len)));return;}'''
    text = replace_once(text, old, new, "editor position persistence")

    text = replace_once(
        text,
        'message("MD Reader 0.6.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت المحلي أو صوت AI من OpenAI، حفظ موضع القراءة، والعلامات المرجعية.',
        'message("MD Reader 0.7.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، والتنقل السريع والعلامات المرجعية.',
        "about version",
    )

    path.write_text(text, encoding="utf-8")


def main():
    patch_reader()
    patch_manifest()
    patch_reading_store()
    patch_main_activity()
    print("Applied MD Reader v0.7 editor UX changes")


if __name__ == "__main__":
    main()
