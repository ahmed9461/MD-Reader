package app.mdreader.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.widget.ImageView;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int REQ_OPEN=1001, REQ_NEW=1002, REQ_SAVE_AS=1003;
    private static final String PREFS="md_reader_preferences", PREF_DARK="dark_mode", PREF_FONT="reader_font_size", PREF_EDITOR_SCROLL="editor_scroll_speed_percent";
    private static final int EDITOR_SCROLL_MIN=10, EDITOR_SCROLL_MAX=500, EDITOR_SCROLL_DEFAULT=100;
    private static final String PREF_TRANSLATION_ENGINE="translation_engine";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService previewIo=Executors.newSingleThreadExecutor();
    private final History history=new History();
    private final TranslationService translator=new TranslationService();
    private AiTranslationService aiTranslator;

    private SharedPreferences prefs;
    private RecentStore recents;
    private ApiKeyStore secrets;
    private DraftStore drafts;
    private ReadingStore reading;
    private SpeechReader speech;
    private LinearLayout root,top,searchBar,formatInner,bottom,homeContent;
    private HorizontalScrollView formatBar;
    private FrameLayout frame;
    private ScrollView home;
    private WebView preview;
    private FlingEditText editor;
    private EditText searchInput;
    private TextView title,searchCount,previewTab,editTab,homeBtn,openBtn,saveBtn,findBtn,moreBtn,quickNavBtn,extraToolsBtn,replaceSearchBtn;
    private Uri currentUri;
    private String currentName="غير محفوظ.md",savedText="";
    private boolean dirty=false,editing=false,homeMode=true,readerReady=false,suppress=false,dark=false;
    private int fontSize=17,editorScrollPercent=EDITOR_SCROLL_DEFAULT,bg,surface,text,muted,border,accent;
    private Runnable saveThen,draftPending,renderPending,editorMaintenancePending;
    private int renderToken=0;
    private static final int PREVIEW_CHUNK_CHARS=90000;
    private static final int LARGE_EDITOR_DEBOUNCE_CHARS=120000, LARGE_HISTORY_LIMIT=20;
    private UiPalette palette;
    private FindReplaceBar findPanel;
    private LinearLayout homeNavigation;
    private final TextView[] homeTabs=new TextView[4];
    private int homePage=0;
    private final int[] homeScroll=new int[4];
    private boolean keyboardVisible=false;
    private Runnable searchPending;
    private BackgroundColorSpan activeSearchHighlight;
    private TextView outlineBtn,bookmarkBtn;
    private Dialog speechDialog;
    private TextView speechStatus,speechProgress,speechPlay,speechRate;

    @Override protected void onCreate(Bundle state){
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE); recents=new RecentStore(prefs); drafts=new DraftStore(this,prefs); reading=new ReadingStore(prefs); secrets=new ApiKeyStore(this); aiTranslator=new AiTranslationService(secrets,prefs);
        dark=prefs.getBoolean(PREF_DARK,false); fontSize=clamp(prefs.getInt(PREF_FONT,17),13,28); editorScrollPercent=clamp(prefs.getInt(PREF_EDITOR_SCROLL,EDITOR_SCROLL_DEFAULT),EDITOR_SCROLL_MIN,EDITOR_SCROLL_MAX);
        setTheme(dark?R.style.Theme_MDReader_Dark:R.style.Theme_MDReader_Light); super.onCreate(state);
        speech=new SpeechReader(this,prefs,new SpeechReader.Listener(){public void onState(SpeechReader.State st){runOnUiThread(()->updateSpeechUi(st));}public void onError(String m){runOnUiThread(()->message("تعذر تشغيل القراءة بالصوت",m));}});
        updatePalette(); buildUi(); applyTheme(); configureEditor(); configureEditorComfort(); enhanceEditorToolbar(); configurePreview();
        Intent i=getIntent();
        if(i!=null&&Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null) load(i.getData(),i.getFlags()); else showHome();
    }

    @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); if(i!=null&&Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null) confirm(() -> load(i.getData(),i.getFlags())); }

    private void buildUi(){
        root=ReaderUi.column(this);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);root.setTag("reader-root");setContentView(root);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
                l=bars.left;t=bars.top;r=bars.right;b=Math.max(bars.bottom,ime.bottom);keyboardVisible=insets.isVisible(WindowInsets.Type.ime());
            }else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
            v.setPadding(l,t,r,b);updateWorkspaceChrome();if(searchBar!=null&&searchBar.getVisibility()==View.VISIBLE)ensureSearchVisible();return insets;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{updateWorkspaceChrome();if(b-t!=ob-ot&&searchBar!=null&&searchBar.getVisibility()==View.VISIBLE)ensureSearchVisible();});
        top=ReaderUi.row(this);top.setPadding(dp(4),0,dp(4),0);root.addView(top,new LinearLayout.LayoutParams(-1,dp(56)));
        homeBtn=iconAction("العودة إلى المكتبة",UiIcon.Kind.BACK,()->confirm(this::showHome));top.addView(homeBtn,ReaderUi.touch(this));
        title=label("MD Reader",17,text,true);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8),0,dp(8),0);title.setOnLongClickListener(v->{if(!homeMode)message("اسم المستند",currentName);return true;});
        top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        openBtn=iconAction("فتح ملف",UiIcon.Kind.FOLDER,this::openPicker); // retained through library/file menu
        saveBtn=iconAction("حفظ الملف",UiIcon.Kind.SAVE,()->save(null));top.addView(saveBtn,ReaderUi.touch(this));
        findBtn=iconAction("البحث داخل الملف",UiIcon.Kind.SEARCH,this::toggleSearch);top.addView(findBtn,ReaderUi.touch(this));
        moreBtn=iconAction("المزيد من الخيارات",UiIcon.Kind.MORE,this::more);top.addView(moreBtn,ReaderUi.touch(this));
        findPanel=new FindReplaceBar(this,palette,new FindReplaceBar.Listener(){
            public void queryChanged(){runSearch(findPanel.query.getText().toString());}
            public void navigate(boolean forward){find(forward);}
            public void close(){closeSearch();}
            public void hideKeyboard(){MainActivity.this.hideKeyboard();}
            public void expandReplacement(){if(!editing)setEditing(true);findPanel.setEditorMode(true);}
            public void replaceOne(){replaceInline(false);}
            public void replaceAll(){replaceInline(true);}
        });
        searchBar=findPanel;searchInput=findPanel.query;searchCount=findPanel.count;replaceSearchBtn=findPanel.expand;
        searchBar.setVisibility(View.GONE);root.addView(searchBar,new LinearLayout.LayoutParams(-1,-2));
        frame=new FrameLayout(this);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        home=new ScrollView(this);home.setFillViewport(true);homeContent=ReaderUi.column(this);homeContent.setPadding(dp(20),dp(16),dp(20),dp(24));
        home.addView(homeContent,new ScrollView.LayoutParams(-1,-2));frame.addView(home,new FrameLayout.LayoutParams(-1,-1));
        preview=new WebView(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        editor=new FlingEditText(this);editor.setTag("markdown-editor");editor.setGravity(Gravity.TOP|Gravity.START);editor.setPadding(dp(20),dp(16),dp(20),dp(28));
        editor.setTextSize(16);editor.setSingleLine(false);editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);editor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);editor.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        editor.setVisibility(View.GONE);frame.addView(editor,new FrameLayout.LayoutParams(-1,-1));
        formatBar=new HorizontalScrollView(this);formatBar.setHorizontalScrollBarEnabled(false);formatBar.setVisibility(View.GONE);
        formatInner=ReaderUi.row(this);formatInner.setPadding(dp(4),0,dp(4),0);formatBar.addView(formatInner,new HorizontalScrollView.LayoutParams(-2,-1));
        root.addView(formatBar,new LinearLayout.LayoutParams(-1,dp(52)));buildFormats();
        bottom=ReaderUi.row(this);bottom.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
        previewTab=tab("قراءة");editTab=tab("تحرير");previewTab.setOnClickListener(v->setEditing(false));editTab.setOnClickListener(v->setEditing(true));
        bottom.addView(previewTab,ReaderUi.weighted());bottom.addView(editTab,ReaderUi.weighted());
        outlineBtn=iconAction("فهرس المستند",UiIcon.Kind.OUTLINE,this::outline);bookmarkBtn=iconAction("العلامات المرجعية",UiIcon.Kind.BOOKMARK,this::bookmarks);
        bottom.addView(outlineBtn,ReaderUi.touch(this));bottom.addView(bookmarkBtn,ReaderUi.touch(this));
        homeNavigation=ReaderUi.row(this);homeNavigation.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(homeNavigation,new LinearLayout.LayoutParams(-1,-2));
        String[] names={"الرئيسية","المستندات","المفضلة","الإعدادات"};
        UiIcon.Kind[] icons={UiIcon.Kind.HOME,UiIcon.Kind.FOLDER,UiIcon.Kind.STAR,UiIcon.Kind.SETTINGS};
        for(int i=0;i<4;i++){
            final int page=i;TextView tab=label(names[i],11,text,false);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(2),dp(6),dp(2),dp(6));tab.setMinHeight(dp(60));
            tab.setCompoundDrawablesRelative(null,new UiIcon(icons[i],muted,dp(22)),null,null);tab.setCompoundDrawablePadding(dp(2));ReaderUi.accessibleAction(tab);
            tab.setContentDescription(names[i]);tab.setOnClickListener(v->switchHomePage(page));homeTabs[i]=tab;homeNavigation.addView(tab,ReaderUi.weighted());
        }
    }

    private void updatePalette(){
        palette=new UiPalette(dark);bg=palette.background;surface=palette.surface;text=palette.text;muted=palette.muted;border=palette.border;accent=palette.accent;
    }
    private TextView iconAction(String label,UiIcon.Kind kind,Runnable action){
        TextView v=ReaderUi.icon(this,label,kind,palette);ReaderUi.accessibleAction(v);v.setOnClickListener(w->action.run());return v;
    }
    private void updateWorkspaceChrome(){
        if(top==null||findPanel==null||bottom==null||formatBar==null)return;
        int available=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom();
        boolean searching=!homeMode&&searchBar.getVisibility()==View.VISIBLE;
        boolean tight=searching&&available>0&&available<dp(300);
        findPanel.setCompact(tight);top.setVisibility(tight?View.GONE:View.VISIBLE);
        // Navigation of matches must leave a useful document viewport, even above the IME.
        boolean focusSearch=searching&&(keyboardVisible||tight);
        bottom.setVisibility(homeMode||focusSearch?View.GONE:View.VISIBLE);
        formatBar.setVisibility(!homeMode&&editing&&!searching?View.VISIBLE:View.GONE);
    }
    private void switchHomePage(int page){
        if(!homeMode)return;homeScroll[homePage]=home.getScrollY();homePage=page;refreshHome();
        home.post(()->home.scrollTo(0,homeScroll[homePage]));
    }

    private void buildFormats(){
        format("↶",v->undo());format("↷",v->redo());divider(); format("H1",v->heading(1));format("H2",v->heading(2));format("H3",v->heading(3));divider();
        format("B",v->wrap("**","**","نص"));format("I",v->wrap("*","*","نص"));format("S",v->wrap("~~","~~","نص"));format("` `",v->wrap("`","`","code"));format("```",v->apply(MarkdownTransforms.fencedCode(txt(),editor.getSelectionStart(),editor.getSelectionEnd())));divider();
        format("❝",v->prefix("> ",false));format("•",v->prefix("- ",false));format("1.",v->prefix("",true));format("☑",v->setTaskState(false));divider();
        format("رابط",v->apply(MarkdownTransforms.link(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),false)));format("صورة",v->apply(MarkdownTransforms.link(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),true)));format("جدول",v->insert("\n| العمود 1 | العمود 2 |\n| --- | --- |\n| قيمة 1 | قيمة 2 |\n"));format("—",v->insert("\n---\n"));format("HTML",v->safeHtmlTools());divider();format("ترجمة",v->translationMenu());
    }

    private void configureEditor(){
        history.reset(""); editor.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable s){
            if(suppress)return;
            if(s.length()>=LARGE_EDITOR_DEBOUNCE_CHARS){
                dirty=true;updateTitle();scheduleEditorMaintenance();
            }else{
                String t=s.toString();dirty=!t.equals(savedText);history.schedule(t);scheduleDraft(t);updateTitle();
                if(searchBar.getVisibility()==View.VISIBLE)editorSearch(searchInput.getText().toString());
            }
        }});
    }

    private void scheduleEditorMaintenance(){
        if(editorMaintenancePending!=null)main.removeCallbacks(editorMaintenancePending);
        editorMaintenancePending=()->{
            editorMaintenancePending=null;
            if(suppress||editor==null)return;
            String t=editor.getText().toString();
            dirty=!t.equals(savedText);history.schedule(t);scheduleDraft(t);updateTitle();
            if(searchBar.getVisibility()==View.VISIBLE)editorSearch(searchInput.getText().toString());
        };
        main.postDelayed(editorMaintenancePending,220);
    }


    private void configureEditorComfort(){
        editor.setScrollContainer(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(true);
        editor.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        editor.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        editor.setFlingSpeedPercent(editorScrollPercent);
        editor.setGravity(Gravity.TOP|Gravity.START);
        editor.setHorizontallyScrolling(false);
        if(Build.VERSION.SDK_INT>=23){
            editor.setScrollIndicators(View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM,
                    View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM);
        }
    }

    private void enhanceEditorToolbar(){
        if(formatInner==null)return;
        quickNavBtn=iconAction("التنقل السريع",UiIcon.Kind.OUTLINE,this::documentNavigation);
        quickNavBtn.setTextSize(21);
        quickNavBtn.setGravity(Gravity.CENTER);
        quickNavBtn.setContentDescription("التنقل السريع داخل المستند");
        quickNavBtn.setTooltipText("التنقل السريع");
        quickNavBtn.setOnClickListener(v->documentNavigation());

        extraToolsBtn=iconAction("أدوات Markdown وHTML",UiIcon.Kind.PLUS,this::extraMarkdownTools);
        extraToolsBtn.setTextSize(23);
        extraToolsBtn.setGravity(Gravity.CENTER);
        extraToolsBtn.setContentDescription("أدوات Markdown وHTML");
        extraToolsBtn.setTooltipText("أدوات Markdown وHTML");
        extraToolsBtn.setOnClickListener(v->extraMarkdownTools());

        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(dp(48),dp(48));
        navLp.setMarginEnd(dp(2));
        LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(dp(48),dp(48));
        toolsLp.setMarginEnd(dp(2));
        formatInner.addView(extraToolsBtn,0,toolsLp);
        formatInner.addView(quickNavBtn,0,navLp);
        styleEditorToolbarExtras();
    }

    private void styleEditorToolbarExtras(){
        if(quickNavBtn!=null){ReaderUi.tintIcons(quickNavBtn,text);quickNavBtn.setBackground(ReaderUi.ripple(this,0,0,12,accent));}
        if(extraToolsBtn!=null){ReaderUi.tintIcons(extraToolsBtn,text);extraToolsBtn.setBackground(ReaderUi.ripple(this,0,0,12,accent));}
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
        sheet("أدوات Markdown وHTML",
                new Action("✅ إجابة صحيحة",()->setTaskState(true),false),
                new Action("☐ اختيار غير محدد",()->setTaskState(false),false),
                new Action("☑ قائمة مهام",this::insertTaskList,false),
                new Action("🧹 توحيد مربعات الاختيار",this::normalizeTaskMarkers,false),
                new Action("◇ HTML آمن",this::safeHtmlTools,false),
                new Action("▣ حاوية بعنوان…",this::titledCodeBlockTool,false),
                new Action("▦ جدول",()->insertMarkdownBlock("| العمود 1 | العمود 2 |"+System.lineSeparator()+"| --- | --- |"+System.lineSeparator()+"| قيمة | قيمة |"),false),
                new Action("— فاصل أفقي",()->insertMarkdownBlock("---"),false),
                new Action("إلغاء",null,false));
    }

    private void titledCodeBlockTool(){
        if(!editing)setEditing(true);
        Dialog d=dialog();LinearLayout p=panel("حاوية بعنوان");
        p.addView(label("اكتب اسم الحاوية. يمكنك استخدام العربية أو الإنجليزية. لغة التلوين اختيارية مثل python أو json؛ اتركها فارغة إذا كانت الحاوية للنص العادي.",13,muted,false),textLp());
        EditText title=inputField("اسم الحاوية","",false);title.setSingleLine(true);p.addView(title,textLp());
        EditText language=inputField("لغة التلوين — اختياري","",false);language.setSingleLine(true);p.addView(language,textLp());
        TextView add=sheetButton("إدراج الحاوية",false),close=sheetButton("إلغاء",false);
        add.setOnClickListener(v->{
            String name=title.getText().toString();
            String lang=language.getText().toString();
            if(MarkdownTransforms.normalizeCodeTitle(name).isEmpty()){title.setError("اكتب اسم الحاوية");return;}
            try{
                MarkdownTransforms.normalizeCodeLanguage(lang);
            }catch(IllegalArgumentException e){
                language.setError("استخدم اسم لغة مثل python أو json أو اتركه فارغًا");
                return;
            }
            dismissSheet(d,p,()->apply(MarkdownTransforms.titledFencedCode(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),name,lang)));
        });
        close.setOnClickListener(v->dismissSheet(d,p,null));
        p.addView(add,buttonLp());p.addView(close,buttonLp());showDialog(d,p,false);title.requestFocus();keyboard(title);
    }

    private void safeHtmlTools(){
        sheet("HTML آمن",
                new Action("🖍 تظليل <mark>",()->apply(SafeHtmlEditorTools.highlight(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("U تسطير <u>",()->apply(SafeHtmlEditorTools.underline(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("A لون النص…",this::safeHtmlTextColorMenu,false),
                new Action("▣ لون الخلفية…",this::safeHtmlBackgroundMenu,false),
                new Action("↔ محاذاة…",this::safeHtmlAlignmentMenu,false),
                new Action("RTL/LTR اتجاه النص…",this::safeHtmlDirectionMenu,false),
                new Action("x² نص علوي <sup>",()->apply(SafeHtmlEditorTools.superscript(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("x₂ نص سفلي <sub>",()->apply(SafeHtmlEditorTools.subscript(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("⌨ مفتاح <kbd>",()->apply(SafeHtmlEditorTools.keyboard(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("ᵃ نص صغير <small>",()->apply(SafeHtmlEditorTools.small(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("▾ تفاصيل قابلة للطي",()->apply(SafeHtmlEditorTools.details(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("↵ سطر HTML <br>",()->apply(SafeHtmlEditorTools.lineBreak(txt(),editor.getSelectionStart(),editor.getSelectionEnd())),false),
                new Action("إلغاء",null,false));
    }

    private void safeHtmlTextColorMenu(){
        sheet("لون النص",
                new Action("أزرق  #0969DA",()->applyHtmlColor(false,"#0969DA"),false),
                new Action("أخضر  #1A7F37",()->applyHtmlColor(false,"#1A7F37"),false),
                new Action("أحمر  #CF222E",()->applyHtmlColor(false,"#CF222E"),false),
                new Action("برتقالي  #BC4C00",()->applyHtmlColor(false,"#BC4C00"),false),
                new Action("بنفسجي  #8250DF",()->applyHtmlColor(false,"#8250DF"),false),
                new Action("رمادي  #656D76",()->applyHtmlColor(false,"#656D76"),false),
                new Action("لون مخصص…",()->customHtmlColor(false),false),
                new Action("إلغاء",null,false));
    }

    private void safeHtmlBackgroundMenu(){
        sheet("لون الخلفية",
                new Action("أصفر فاتح  #FFF8C5",()->applyHtmlColor(true,"#FFF8C5"),false),
                new Action("أخضر فاتح  #DAFBE1",()->applyHtmlColor(true,"#DAFBE1"),false),
                new Action("أزرق فاتح  #DDF4FF",()->applyHtmlColor(true,"#DDF4FF"),false),
                new Action("أحمر فاتح  #FFEBE9",()->applyHtmlColor(true,"#FFEBE9"),false),
                new Action("بنفسجي فاتح  #FBEFFF",()->applyHtmlColor(true,"#FBEFFF"),false),
                new Action("رمادي فاتح  #F6F8FA",()->applyHtmlColor(true,"#F6F8FA"),false),
                new Action("لون مخصص…",()->customHtmlColor(true),false),
                new Action("إلغاء",null,false));
    }

    private void safeHtmlAlignmentMenu(){
        sheet("محاذاة HTML",
                new Action("توسيط",()->apply(SafeHtmlEditorTools.alignment(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"center")),false),
                new Action("يمين",()->apply(SafeHtmlEditorTools.alignment(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"right")),false),
                new Action("يسار",()->apply(SafeHtmlEditorTools.alignment(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"left")),false),
                new Action("ضبط",()->apply(SafeHtmlEditorTools.alignment(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"justify")),false),
                new Action("إلغاء",null,false));
    }

    private void safeHtmlDirectionMenu(){
        sheet("اتجاه النص",
                new Action("RTL — من اليمين لليسار",()->apply(SafeHtmlEditorTools.direction(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"rtl")),false),
                new Action("LTR — من اليسار لليمين",()->apply(SafeHtmlEditorTools.direction(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"ltr")),false),
                new Action("Auto — تلقائي",()->apply(SafeHtmlEditorTools.direction(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),"auto")),false),
                new Action("إلغاء",null,false));
    }

    private void applyHtmlColor(boolean background,String color){
        if(background)apply(SafeHtmlEditorTools.backgroundColor(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),color));
        else apply(SafeHtmlEditorTools.textColor(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),color));
    }

    private void customHtmlColor(boolean background){
        if(!editing)setEditing(true);
        Dialog d=dialog();LinearLayout p=panel(background?"لون خلفية مخصص":"لون نص مخصص");
        p.addView(label("اكتب اللون بصيغة HEX مثل #0969DA أو #FFF8C5. يتم التحقق منه قبل الإدراج، ثم تعيد طبقة HTML الآمنة فحصه عند المعاينة.",13,muted,false),textLp());
        EditText value=inputField("#RRGGBB","",false);value.setSingleLine(true);p.addView(value,textLp());
        TextView add=sheetButton("إدراج اللون",false),close=sheetButton("إلغاء",false);
        add.setOnClickListener(v->{String color=value.getText().toString().trim();if(!SafeHtmlEditorTools.isSafeColor(color)){value.setError("استخدم لون HEX صحيحًا مثل #0969DA");return;}dismissSheet(d,p,()->applyHtmlColor(background,color));});
        close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(add,buttonLp());p.addView(close,buttonLp());showDialog(d,p,false);value.requestFocus();keyboard(value);
    }

    private void insertMarkdownBlock(String block){
        if(!editing)setEditing(true);
        Editable e=editor.getText();
        int a=Math.max(0,Math.min(editor.getSelectionStart(),editor.getSelectionEnd()));
        int b=Math.max(a,Math.max(editor.getSelectionStart(),editor.getSelectionEnd()));
        String before=a>0&&e.charAt(a-1)!='\n'?System.lineSeparator()+System.lineSeparator():"";
        String after=b<e.length()&&e.charAt(b)!='\n'?System.lineSeparator()+System.lineSeparator():"";
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

    private String trimLeadingCompat(String value){
        if(value==null)return "";
        int p=0;
        while(p<value.length()&&Character.isWhitespace(value.charAt(p)))p++;
        return p==0?value:value.substring(p);
    }

    private int taskMarkerState(String body){
        if(body==null)return -1;
        String value=trimLeadingCompat(body);
        if(value.startsWith("✅"))return 1;
        if(value.isEmpty())return -1;
        char bullet=value.charAt(0);
        if(bullet!='-'&&bullet!='*'&&bullet!='+')return -1;
        int p=1;
        while(p<value.length()&&(value.charAt(p)==' '||value.charAt(p)=='\t'))p++;
        if(p>=value.length()||value.charAt(p)!='[')return -1;
        int close=value.indexOf(']',p+1);
        if(close<0)return -1;
        String state=value.substring(p+1,close).trim();
        if(state.isEmpty())return 0;
        return state.equalsIgnoreCase("x")?1:-1;
    }

    private String taskBody(String body){
        if(body==null)return "";
        String value=trimLeadingCompat(body);
        if(value.startsWith("✅"))return trimLeadingCompat(value.substring("✅".length()));
        int state=taskMarkerState(value);
        if(state<0)return value;
        int p=1;
        while(p<value.length()&&(value.charAt(p)==' '||value.charAt(p)=='\t'))p++;
        int close=value.indexOf(']',p+1);
        return close<0?value:trimLeadingCompat(value.substring(close+1));
    }

    private String taskLine(String line,boolean checked){
        if(line==null)return "";
        int p=0;
        while(p<line.length()&&(line.charAt(p)==' '||line.charAt(p)=='\t'))p++;
        String indent=line.substring(0,p),body=line.substring(p);
        String clean=taskBody(body);
        if(clean.trim().isEmpty())return line;
        return indent+"- ["+(checked?"x":" ")+"] "+clean;
    }

    private int[] selectedLineBounds(){
        String all=txt();
        int a=Math.max(0,Math.min(editor.getSelectionStart(),editor.getSelectionEnd()));
        int b=Math.max(a,Math.max(editor.getSelectionStart(),editor.getSelectionEnd()));
        int start=all.lastIndexOf('\n',Math.max(0,a-1));start=start<0?0:start+1;
        int end=all.indexOf('\n',b);if(end<0)end=all.length();
        return new int[]{start,end};
    }

    private void setTaskState(boolean checked){
        if(!editing)setEditing(true);
        int[] bounds=selectedLineBounds();
        Editable e=editor.getText();
        String selected=e.subSequence(bounds[0],bounds[1]).toString();
        String[] lines=selected.split("\\n",-1);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            out.append(taskLine(lines[i],checked));
        }
        e.replace(bounds[0],bounds[1],out.toString());
        int end=Math.min(e.length(),bounds[0]+out.length());
        editor.setSelection(end);
        editor.bringPointIntoView(end);
        Toast.makeText(this,checked?"تم تحديد الإجابة الصحيحة":"تم إنشاء اختيار غير محدد",Toast.LENGTH_SHORT).show();
    }

    private void normalizeTaskMarkers(){
        if(!editing)setEditing(true);
        String old=txt();
        String[] lines=old.split("\\n",-1);
        StringBuilder out=new StringBuilder(old.length()+32);
        int changed=0;
        for(int i=0;i<lines.length;i++){
            if(i>0)out.append('\n');
            String line=lines[i];
            int p=0;while(p<line.length()&&(line.charAt(p)==' '||line.charAt(p)=='\t'))p++;
            String body=line.substring(p);
            int state=taskMarkerState(body);
            if(state>=0){String normalized=taskLine(line,state==1);out.append(normalized);if(!normalized.equals(line))changed++;}
            else out.append(line);
        }
        if(changed==0){Toast.makeText(this,"مربعات الاختيار سليمة بالفعل",Toast.LENGTH_SHORT).show();return;}
        history.checkpoint(old);
        int oldCaret=Math.max(0,editor.getSelectionStart());
        String next=out.toString();
        suppress=true;editor.setText(next);editor.setSelection(Math.min(next.length(),oldCaret));suppress=false;
        history.checkpoint(next);dirty=!next.equals(savedText);scheduleDraft(next);updateTitle();
        Toast.makeText(this,"تم توحيد "+changed+" سطر",Toast.LENGTH_SHORT).show();
    }

    private void configurePreview(){
        WebView.setWebContentsDebuggingEnabled(false); preview.setBackgroundColor(Color.TRANSPARENT); preview.getSettings().setJavaScriptEnabled(true); preview.getSettings().setDomStorageEnabled(false); preview.getSettings().setAllowFileAccess(true); preview.getSettings().setAllowContentAccess(true); preview.addJavascriptInterface(new Bridge(),"Android");
        preview.setFindListener((active,total,done)->{if(done&&!editing&&searchBar.getVisibility()==View.VISIBLE)findPanel.setResults(total==0?0:active+1,total);});
        preview.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){readerReady=true;render();restoreReadingPosition();}@Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){Uri u=r.getUrl();String s=u.getScheme();if(s!=null&&(s.equals("http")||s.equals("https")||s.equals("mailto")||s.equals("tel"))){external(u.toString());return true;}return false;}});
        preview.loadUrl("file:///android_asset/reader.html");
    }

    private void applyTheme(){
        updatePalette();root.setBackgroundColor(bg);top.setBackgroundColor(surface);formatBar.setBackgroundColor(surface);formatInner.setBackgroundColor(surface);
        bottom.setBackgroundColor(surface);homeNavigation.setBackgroundColor(surface);home.setBackgroundColor(bg);homeContent.setBackgroundColor(bg);
        editor.setBackgroundColor(bg);editor.setTextColor(text);editor.setHintTextColor(muted);editor.setHighlightColor(UiPalette.alpha(accent,70));title.setTextColor(text);
        for(TextView v:new TextView[]{homeBtn,saveBtn,findBtn,moreBtn,outlineBtn,bookmarkBtn}){ReaderUi.tintIcons(v,text);v.setBackground(ReaderUi.ripple(this,0,0,12,accent));}
        tint(formatInner);for(int i=0;i<formatInner.getChildCount();i++)if(Boolean.TRUE.equals(formatInner.getChildAt(i).getTag()))formatInner.getChildAt(i).setBackgroundColor(border);
        findPanel.theme(palette);styleTabs();styleEditorToolbarExtras();
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        if(Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(dark?0:mask,mask);}}
        else{int f=getWindow().getDecorView().getSystemUiVisibility();int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(dark?f&~mask:f|mask);}
        render();if(homeMode)refreshHome();
    }

    private void showHome(){
        if(!homeMode)saveReadingPosition();homeMode=true;closeSearch();home.setVisibility(View.VISIBLE);preview.setVisibility(View.GONE);editor.setVisibility(View.GONE);
        homeNavigation.setVisibility(View.VISIBLE);homeBtn.setVisibility(View.GONE);openBtn.setVisibility(View.GONE);saveBtn.setVisibility(View.GONE);findBtn.setVisibility(View.GONE);
        refreshHome();updateWorkspaceChrome();hideKeyboard();
    }
    private void showDocument(){
        homeMode=false;home.setVisibility(View.GONE);homeNavigation.setVisibility(View.GONE);homeBtn.setVisibility(View.VISIBLE);saveBtn.setVisibility(View.VISIBLE);findBtn.setVisibility(View.VISIBLE);
        editor.setVisibility(editing?View.VISIBLE:View.GONE);preview.setVisibility(editing?View.GONE:View.VISIBLE);findPanel.setEditorMode(editing);
        if(!editing)render();updateTitle();styleTabs();updateWorkspaceChrome();
    }

    private void refreshHome(){
        homeContent.removeAllViews();String[] names={"MD Reader","المستندات","المفضلة","الإعدادات"};title.setText(names[homePage]);
        for(int i=0;i<homeTabs.length;i++)ReaderUi.selected(homeTabs[i],homePage==i,palette);
        if(homePage==3){buildSettingsHome();return;}
        if(homePage==0){
            LinearLayout hero=ReaderUi.row(this);ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_launcher_art);mark.setContentDescription("شعار MD Reader");
            mark.setScaleType(ImageView.ScaleType.FIT_CENTER);hero.addView(mark,new LinearLayout.LayoutParams(dp(64),dp(64)));
            LinearLayout words=ReaderUi.column(this);words.setPadding(dp(12),0,dp(12),0);words.addView(label("مساحتك للقراءة والكتابة",22,text,true));words.addView(label("Markdown، ببساطة.",14,muted,false));hero.addView(words,ReaderUi.weighted());homeContent.addView(hero,textLp());
            LinearLayout actions=ReaderUi.row(this);TextView open=homeButton("فتح ملف",true),fresh=homeButton("ملف جديد",false);open.setOnClickListener(v->openPicker());fresh.setOnClickListener(v->newFile());
            actions.addView(open,weight(1,0,4));actions.addView(fresh,weight(1,4,0));homeContent.addView(actions,new LinearLayout.LayoutParams(-1,-2));
            TextView github=sheetButton("تنزيل ملف من GitHub",false);github.setOnClickListener(v->githubImport());homeContent.addView(github,buttonLp());
            DraftStore.Draft d=drafts.read();if(d!=null){
                section("تابع من حيث توقفت");LinearLayout c=card();c.addView(label(d.name,16,text,true));c.addView(label("مسودة غير محفوظة • "+time(d.time),12,muted,false));
                LinearLayout row=ReaderUi.row(this);TextView restore=mini("استعادة",true),drop=iconAction("حذف المسودة",UiIcon.Kind.ERASE,()->choice("حذف المسودة؟","لن تتمكن من استعادتها بعد الحذف.",new Action("حذف المسودة",()->{drafts.clear();refreshHome();},true),new Action("إلغاء",null,false)));
                restore.setOnClickListener(v->restore(d));row.addView(restore,ReaderUi.weighted());row.addView(drop,ReaderUi.touch(this));c.addView(row,textLp());homeContent.addView(c,cardLp());
            }
        }else{
            homeContent.addView(label(names[homePage],26,text,true),textLp());homeContent.addView(label(homePage==2?"ملفاتك المهمة، في مكان واحد.":"الملفات التي فتحتها مؤخرًا على جهازك.",14,muted,false),textLp());
            if(homePage==1){TextView open=homeButton("فتح ملف من الجهاز",true);open.setOnClickListener(v->openPicker());homeContent.addView(open,buttonLp());}
        }
        List<RecentStore.Entry> entries=homePage==2?recents.favorites():recents.all();
        if(homePage==0)section("أُضيفت مؤخرًا");
        if(entries.isEmpty()){
            TextView empty=label(homePage==2?"لا توجد مفضلة بعد\nاضغط النجمة بجانب أي مستند لإضافته.":"لا توجد مستندات بعد\nافتح ملف Markdown لتبدأ.",15,muted,false);
            empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(28),dp(16),dp(28));homeContent.addView(empty,cardLp());
        }else{
            int limit=homePage==0?Math.min(3,entries.size()):entries.size();for(int i=0;i<limit;i++)docCard(entries.get(i));
            if(homePage==0&&entries.size()>3){TextView all=sheetButton("عرض جميع المستندات",false);all.setOnClickListener(v->switchHomePage(1));homeContent.addView(all,buttonLp());}
        }
    }

    private void buildSettingsHome(){
        homeContent.addView(label("اضبط مساحتك",26,text,true),textLp());
        homeContent.addView(label("مظهر مريح وأدوات تناسب طريقتك.",14,muted,false),textLp());
        section("المظهر والقراءة");
        homeSetting(dark?"المظهر الداكن • التبديل إلى الفاتح":"المظهر الفاتح • التبديل إلى الداكن",UiIcon.Kind.MOON,this::toggleTheme);
        homeSetting("حجم خط القراءة • "+fontSize,UiIcon.Kind.TEXT,this::fontDialog);
        homeSetting("سرعة تمرير المحرر • "+editorScrollPercent+"%",UiIcon.Kind.SETTINGS,this::editorScrollSettings);
        section("اللغة والصوت");homeSetting("إعدادات الترجمة",UiIcon.Kind.TEXT,this::translationSettings);
        homeSetting("إعدادات القراءة بالصوت",UiIcon.Kind.SPEAKER,()->SpeechSettingsDialog.show(this,speech));
        section("MD Reader");homeSetting("حول التطبيق • 0.13.0",UiIcon.Kind.INFO,this::about);
        TextView privacy=label("لا إعلانات • لا تحليلات • لا تتبع",13,muted,false);privacy.setGravity(Gravity.CENTER);homeContent.addView(privacy,textLp());
    }
    private void homeSetting(String name,UiIcon.Kind icon,Runnable action){
        TextView v=sheetButton(name,false);v.setCompoundDrawablesRelative(new UiIcon(icon,accent,dp(22)),null,null,null);v.setOnClickListener(w->action.run());homeContent.addView(v,buttonLp());
    }

    private void section(String s){TextView v=label(s,18,text,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(24);lp.bottomMargin=dp(10);homeContent.addView(v,lp);}
    private void docCard(RecentStore.Entry e){
        LinearLayout c=card();c.setPadding(dp(12),dp(8),dp(8),dp(8));LinearLayout row=ReaderUi.row(this);
        LinearLayout info=ReaderUi.column(this);info.setMinimumHeight(dp(56));info.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=label(e.name,15,text,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);info.addView(name);
        info.addView(label(time(e.openedAt),12,muted,false));info.setOnClickListener(v->load(e.asUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        info.setBackground(ReaderUi.ripple(this,0,0,12,accent));info.setFocusable(true);info.setContentDescription("فتح "+e.name);row.addView(info,ReaderUi.weighted());
        TextView star=iconAction(e.favorite?"إزالة من المفضلة":"إضافة إلى المفضلة",UiIcon.Kind.STAR,()->{recents.setFavorite(e.uri,!e.favorite);refreshHome();});ReaderUi.selected(star,e.favorite,palette);row.addView(star,ReaderUi.touch(this));
        TextView more=iconAction("خيارات "+e.name,UiIcon.Kind.MORE,()->recentActions(e));row.addView(more,ReaderUi.touch(this));c.addView(row);homeContent.addView(c,cardLp());
    }
    private void recentActions(RecentStore.Entry e){sheet(e.name,new Action(e.favorite?"إزالة من المفضلة":"إضافة إلى المفضلة",()->{recents.setFavorite(e.uri,!e.favorite);refreshHome();},false),new Action("إزالة من السجل",()->{recents.remove(e.uri);refreshHome();},true),new Action("إلغاء",null,false));}
    private void restore(DraftStore.Draft d){currentName=d.name;currentUri=null;if(d.uri!=null&&!d.uri.isEmpty())try{currentUri=Uri.parse(d.uri);}catch(Exception ignored){}setText(d.text,false);dirty=true;showDocument();setEditing(true);}

    private void githubImport(){confirm(this::githubImportDialog);}

    private void githubImportDialog(){
        Dialog d=dialog();LinearLayout p=panel("تنزيل Markdown من GitHub");
        p.addView(label("ألصق رابط ملف Markdown من GitHub. يدعم روابط github.com التي تحتوي /blob/ وروابط raw.githubusercontent.com العامة.",13,muted,false),textLp());
        EditText link=multiLineField("https://github.com/user/repo/blob/main/file.md","",1);link.setMinLines(2);link.setMaxLines(4);link.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI|InputType.TYPE_TEXT_FLAG_MULTI_LINE);p.addView(link,textLp());
        TextView status=label("مثال: https://github.com/ahmed9461/GitDock/blob/main/CHANGELOG.md",12,muted,false);status.setTextIsSelectable(true);p.addView(status,textLp());
        TextView open=sheetButton("تنزيل وفتح",false),save=sheetButton("تنزيل وحفظ في الجهاز",false),close=sheetButton("إلغاء",false);
        Runnable reset=()->{open.setEnabled(true);save.setEnabled(true);open.setAlpha(1f);save.setAlpha(1f);};
        View.OnClickListener start=v->{boolean saveAfter=v==save;String value=link.getText().toString().trim();try{GitHubMarkdownSource.parse(value);}catch(Exception e){link.setError("استخدم رابط ملف Markdown مباشرًا من GitHub");return;}open.setEnabled(false);save.setEnabled(false);open.setAlpha(.55f);save.setAlpha(.55f);status.setText("جاري التنزيل…");io.execute(()->{try{GitHubMarkdownSource.DownloadedFile file=GitHubMarkdownSource.download(value);main.post(()->{if(!d.isShowing())return;status.setText("تم تنزيل "+file.fileName);dismissSheet(d,p,()->openGitHubDownload(file,saveAfter));});}catch(Exception e){main.post(()->{if(!d.isShowing())return;status.setText("تعذر التنزيل: "+GitHubMarkdownSource.userMessage(e));reset.run();});}});};
        open.setOnClickListener(start);save.setOnClickListener(start);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(open,buttonLp());p.addView(save,buttonLp());p.addView(close,buttonLp());
        showDialog(d,p,false);link.requestFocus();keyboard(link);
    }

    private void openGitHubDownload(GitHubMarkdownSource.DownloadedFile file,boolean saveAfter){
        saveReadingPosition();currentUri=null;currentName=ensureMd(file.fileName);setText(file.text,true);dirty=false;drafts.clear();showDocument();setEditing(false);Toast.makeText(this,"تم تنزيل "+currentName,Toast.LENGTH_SHORT).show();if(saveAfter)saveAs();
    }

    private void openPicker(){confirm(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/markdown","text/x-markdown","text/plain","application/octet-stream"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_OPEN);});}
    private void newFile(){confirm(()->{saveReadingPosition();startActivityForResult(createIntent("document.md"),REQ_NEW);});}
    private void saveAs(){saveReadingPosition();startActivityForResult(createIntent(ensureMd(currentName)),REQ_SAVE_AS);}
    private Intent createIntent(String name){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/markdown");i.putExtra(Intent.EXTRA_TITLE,ensureMd(name));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);return i;}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(req==REQ_SAVE_AS)saveThen=null;return;}Uri u=data.getData();takePermission(u,data.getFlags());if(req==REQ_OPEN)load(u,data.getFlags());else if(req==REQ_NEW){currentUri=u;currentName=name(u);recents.touch(u,currentName);setText("",false);showDocument();setEditing(true);save(null);}else if(req==REQ_SAVE_AS){String oldKey=currentDocKey();currentUri=u;currentName=name(u);if(!oldKey.isEmpty())reading.move(oldKey,currentDocKey());recents.touch(u,currentName);Runnable r=saveThen;saveThen=null;save(r);}}

    private void load(Uri u,int flags){saveReadingPosition();takePermission(u,flags);io.execute(()->{try{String t=read(u),n=name(u);main.post(()->{currentUri=u;currentName=n;recents.touch(u,n);setText(t,true);showDocument();setEditing(false);restoreReadingPosition();});}catch(Exception e){main.post(()->error("تعذر فتح الملف",e));}});}
    private void save(Runnable after){if(currentUri==null){saveThen=after;saveAs();return;}String t=txt();Uri u=currentUri;io.execute(()->{try{write(u,t);main.post(()->{savedText=t;dirty=false;drafts.clear();recents.touch(u,currentName);updateTitle();Toast.makeText(this,"تم الحفظ",Toast.LENGTH_SHORT).show();if(after!=null)after.run();});}catch(Exception e){main.post(()->error("تعذر حفظ الملف",e));}});}
    private void setText(String t,boolean saved){if(t==null)t="";suppress=true;editor.setText(t);editor.setSelection(0);suppress=false;savedText=saved?t:"";dirty=!t.equals(savedText);history.reset(t);updateTitle();render();}
    private String read(Uri u)throws Exception{try(InputStream in=getContentResolver().openInputStream(u)){if(in==null)throw new IllegalStateException("Input stream unavailable");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);byte[] data=out.toByteArray();int off=data.length>=3&&(data[0]&255)==0xef&&(data[1]&255)==0xbb&&(data[2]&255)==0xbf?3:0;return new String(data,off,data.length-off,StandardCharsets.UTF_8);}}
    private void write(Uri u,String t)throws Exception{try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){if(out==null)throw new IllegalStateException("Output stream unavailable");out.write(t.getBytes(StandardCharsets.UTF_8));out.flush();}}
    private String name(Uri u){if(u!=null&&"content".equals(u.getScheme()))try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&c.getString(i)!=null)return c.getString(i);}}catch(Exception ignored){}String p=u==null?null:u.getLastPathSegment();return p==null||p.isEmpty()?"document.md":p;}
    private void takePermission(Uri u,int flags){if(u==null||!"content".equals(u.getScheme()))return;try{int f=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if(f!=0)getContentResolver().takePersistableUriPermission(u,f);}catch(Exception ignored){}}

    private void setEditing(boolean on){
        if(homeMode)showDocument();if(on==editing)return;saveReadingPosition();editing=on;
        if(on){preview.setVisibility(View.GONE);editor.setVisibility(View.VISIBLE);restoreEditorPosition();}
        else{render();editor.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);hideKeyboard();preview.postDelayed(this::restoreReadingPosition,220);}
        findPanel.setEditorMode(on);clearSearch();styleTabs();updateWorkspaceChrome();
        if(searchBar.getVisibility()==View.VISIBLE)runSearch(searchInput.getText().toString());
    }
    private void styleTabs(){if(previewTab==null)return;styleTab(previewTab,!editing);styleTab(editTab,editing);}
    private void styleTab(TextView v,boolean selected){ReaderUi.selected(v,selected,palette);}
    private void render(){
        if(!readerReady||preview==null||homeMode||editing)return;
        if(renderPending!=null)main.removeCallbacks(renderPending);
        final int token=++renderToken;
        renderPending=()->{
            renderPending=null;
            if(token!=renderToken||preview==null||homeMode||editing)return;
            final String source=txt();
            final String theme=dark?"dark":"light";
            final int size=fontSize;
            previewIo.execute(()->{
                final String encoded=Base64.encodeToString(source.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
                main.post(()->{
                    if(token!=renderToken||preview==null||homeMode||editing)return;
                    if(encoded.length()<=PREVIEW_CHUNK_CHARS){
                        preview.evaluateJavascript("window.renderMarkdown('"+encoded+"','"+theme+"',"+size+");",null);
                    }else{
                        preview.evaluateJavascript("window.beginChunkedMarkdown('"+theme+"',"+size+");",v->sendPreviewChunk(encoded,0,token));
                    }
                });
            });
        };
        main.postDelayed(renderPending,32);
    }

    private void sendPreviewChunk(String encoded,int offset,int token){
        if(token!=renderToken||preview==null||homeMode||editing)return;
        if(offset>=encoded.length()){
            preview.evaluateJavascript("window.finishChunkedMarkdown();",null);
            return;
        }
        int end=Math.min(encoded.length(),offset+PREVIEW_CHUNK_CHARS);
        if(end<encoded.length())end-=((end-offset)%4);
        if(end<=offset)end=Math.min(encoded.length(),offset+4);
        String chunk=encoded.substring(offset,end);
        final int next=end;
        preview.evaluateJavascript("window.appendMarkdownChunk('"+chunk+"');",v->sendPreviewChunk(encoded,next,token));
    }

    private void more(){
        if(homeMode){sheet("المكتبة",new Action("فتح ملف",this::openPicker,false),new Action("ملف جديد",this::newFile,false),new Action("تنزيل من GitHub",this::githubImport,false),new Action("الإعدادات",()->switchHomePage(3),false),new Action("حول التطبيق",this::about,false));return;}
        Dialog d=dialog();LinearLayout p=panel("أدوات المستند");
        addActionGroup(d,p,"الملف",new Action("فتح ملف",this::openPicker,false),new Action("ملف جديد",this::newFile,false),new Action("حفظ باسم",this::saveAs,false),new Action("تنزيل من GitHub",this::githubImport,false),new Action("مشاركة الملف",this::share,false),new Action("تصدير PDF",this::pdf,false));
        addActionGroup(d,p,"القراءة والتنقل",new Action("الفهرس",this::outline,false),new Action("التنقل السريع",this::documentNavigation,false),new Action("بحث واستبدال",this::searchReplaceDialog,false),new Action("إضافة علامة مرجعية",this::addBookmark,false),new Action("العلامات المرجعية",this::bookmarks,false));
        addActionGroup(d,p,"الأدوات والمظهر",new Action("القراءة بالصوت",this::speechMenu,false),new Action("الترجمة",this::translationMenu,false),new Action("حجم خط القراءة",this::fontDialog,false),new Action("سرعة تمرير التحرير",this::editorScrollSettings,false),new Action(dark?"الوضع الفاتح":"الوضع الداكن",this::toggleTheme,false),new Action("حول التطبيق",this::about,false));
        showDialog(d,p,true);
    }
    private void addActionGroup(Dialog d,LinearLayout p,String heading,Action... actions){
        TextView h=label(heading,12,accent,true);h.setPadding(dp(12),dp(14),dp(12),dp(4));p.addView(h,textLp());
        for(Action a:actions){TextView v=sheetButton(a.text,a.danger);v.setOnClickListener(w->dismissSheet(d,p,a.run));p.addView(v,buttonLp());}
    }
    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean(PREF_DARK,dark).apply();applyTheme();styleEditorToolbarExtras();}
    private void fontDialog(){Dialog d=dialog();LinearLayout p=panel("حجم خط القراءة");TextView val=label(fontSize+" px",19,text,false);val.setGravity(Gravity.CENTER);SeekBar s=new SeekBar(this);s.setMax(15);s.setProgress(fontSize-13);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int x,boolean f){fontSize=13+x;val.setText(fontSize+" px");render();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});p.addView(val,new LinearLayout.LayoutParams(-1,dp(44)));p.addView(s,new LinearLayout.LayoutParams(-1,dp(54)));TextView done=sheetButton("تم",false);done.setOnClickListener(v->{prefs.edit().putInt(PREF_FONT,fontSize).apply();dismissSheet(d,p,null);});p.addView(done,buttonLp());showDialog(d,p,false);}
    private void outline(){List<OutlineParser.Heading> hs=OutlineParser.parse(txt());if(hs.isEmpty()){Toast.makeText(this,"لا توجد عناوين في الملف",Toast.LENGTH_SHORT).show();return;}Dialog d=dialog();LinearLayout p=panel("فهرس المستند");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);for(int i=0;i<hs.size();i++){int idx=i;OutlineParser.Heading h=hs.get(i);TextView r=sheetButton(repeat("   ",Math.max(0,h.level-1))+h.title,false);r.setOnClickListener(v->dismissSheet(d,p,()->{if(editing){jumpEditorOffset(Math.min(h.offset,editor.length()));}else preview.evaluateJavascript("window.scrollToHeading("+idx+");",null);}));list.addView(r,buttonLp());}p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());showDialog(d,p,true);}
    private void pdf(){if(homeMode)return;render();if(editing)setEditing(false);preview.postDelayed(()->{try{PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);PrintDocumentAdapter a=preview.createPrintDocumentAdapter(stripMd(currentName));pm.print(stripMd(currentName),a,null);}catch(Exception e){error("تعذر بدء التصدير",e);}},650);}
    private void share(){if(homeMode)return;if(currentUri==null||dirty){save(this::share);return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/markdown");i.putExtra(Intent.EXTRA_STREAM,currentUri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"مشاركة الملف"));}catch(Exception e){error("تعذر مشاركة الملف",e);}}
    private void about(){message("MD Reader 0.13.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، عناوين مخصصة لحاويات الكود، طبقة HTML آمنة ومفلترة مع أدوات جاهزة للتظليل والألوان والمحاذاة والتفاصيل، مربعات الاختيار والإجابات الصحيحة المظللة، أدوات مباشرة لتحديد الإجابة الصحيحة وتوحيد مربعات الاختيار، معاينة محسنة للملفات الكبيرة، تنزيل ملفات Markdown العامة مباشرة من GitHub، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.\n\nلا إعلانات • لا تحليلات • لا تتبع\n\nمفتاح API الشخصي يُحفظ مشفرًا على الجهاز.");}

    private void speechMenu(){
        List<Action>a=new ArrayList<>();
        if(editing){
            if(editor.getSelectionStart()!=editor.getSelectionEnd())a.add(new Action("قراءة المحدد",()->speakEditorRange(selectedRange()),false));
            a.add(new Action("قراءة الفقرة الحالية",()->speakEditorRange(currentParagraphRange()),false));
        }else a.add(new Action("قراءة النص المحدد في المعاينة",this::speakPreviewSelection,false));
        a.add(new Action("قراءة المستند كاملًا",()->startSpeech(txt()),false));
        if(speech!=null&&speech.isActive())a.add(new Action("فتح مشغل الصوت",this::openSpeechPlayer,false));
        a.add(new Action("إعدادات الصوت",()->SpeechSettingsDialog.show(this,speech),false));
        a.add(new Action("إلغاء",null,false));
        sheet("القراءة بالصوت",a.toArray(new Action[0]));
    }
    private void speakEditorRange(int[] range){if(range==null||range.length<2)return;String t=txt();int a=Math.max(0,Math.min(range[0],t.length())),b=Math.max(a,Math.min(range[1],t.length()));if(b<=a){Toast.makeText(this,"لا يوجد نص للقراءة",Toast.LENGTH_SHORT).show();return;}startSpeech(t.substring(a,b));}
    private void speakPreviewSelection(){preview.evaluateJavascript("(function(){return window.getSelection?window.getSelection().toString():'';})()",value->{String selected=jsonString(value);runOnUiThread(()->{if(selected.trim().isEmpty()){Toast.makeText(this,"حدد النص أولًا في وضع المعاينة",Toast.LENGTH_SHORT).show();return;}startSpeech(selected);});});}
    private void startSpeech(String source){List<MarkdownSpeechPlan.Block> blocks=MarkdownSpeechPlan.blocks(source);if(blocks.isEmpty()){Toast.makeText(this,"لا يوجد نص مناسب للقراءة",Toast.LENGTH_SHORT).show();return;}speech.start(blocks);openSpeechPlayer();}
    private void openSpeechPlayer(){
        if(speechDialog!=null&&speechDialog.isShowing()){updateSpeechUi(speech.state());return;}
        Dialog d=dialog();LinearLayout p=panel("القراءة بالصوت");speechDialog=d;
        speechStatus=label("جاري تجهيز محرك الصوت…",16,text,true);speechStatus.setMaxLines(4);speechStatus.setPadding(dp(10),dp(10),dp(10),dp(10));speechStatus.setBackground(round(bg,border,12));p.addView(speechStatus,textLp());
        speechProgress=label("",13,muted,false);speechProgress.setGravity(Gravity.CENTER);p.addView(speechProgress,textLp());
        LinearLayout controls=new LinearLayout(this);TextView prev=mini("السابق",false);speechPlay=mini("⏸ إيقاف مؤقت",true);TextView next=mini("التالي",false);prev.setOnClickListener(v->speech.previous());speechPlay.setOnClickListener(v->{if(speech.isPaused())speech.resume();else speech.pause();});next.setOnClickListener(v->speech.next());controls.addView(prev,weight(1,0,4));controls.addView(speechPlay,weight(1,4,4));controls.addView(next,weight(1,4,0));p.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        speechRate=sheetButton("السرعة",false);speechRate.setGravity(Gravity.CENTER);speechRate.setOnClickListener(v->{float r=speech.rate();float n=r<.74f?.75f:r<.99f?1f:r<1.24f?1.25f:r<1.49f?1.5f:r<1.99f?2f:.5f;speech.setRate(n);});p.addView(speechRate,buttonLp());
        TextView stop=sheetButton("إيقاف القراءة",true);stop.setOnClickListener(v->speech.stop());p.addView(stop,buttonLp());TextView hide=sheetButton("إخفاء المشغل",false);hide.setOnClickListener(v->dismissSheet(d,p,null));p.addView(hide,buttonLp());
        d.setOnDismissListener(x->{if(speechDialog==d){speechDialog=null;speechStatus=null;speechProgress=null;speechPlay=null;speechRate=null;}});showDialog(d,p,false);updateSpeechUi(speech.state());
    }
    private void updateSpeechUi(SpeechReader.State st){if(st==null)return;if(speechStatus!=null){String value=st.text==null||st.text.trim().isEmpty()?(st.ready?"لا توجد قراءة نشطة":"جاري تجهيز محرك الصوت…"):st.text;speechStatus.setText(value);}if(speechProgress!=null){String lang=st.arabic?"العربية":"English";speechProgress.setText(st.total<=0?(st.ready?"جاهز":"جاري التجهيز"):(st.active||st.paused?"الفقرة "+(st.index+1)+" من "+st.total+" • "+lang:"اكتملت القراءة"));}if(speechPlay!=null){speechPlay.setText(st.paused?"▶ استئناف":st.active?"⏸ إيقاف مؤقت":"▶ تشغيل");speechPlay.setEnabled(st.active||st.paused);}if(speechRate!=null)speechRate.setText("سرعة القراءة: "+rateLabel(st.rate));}
    private String rateLabel(float r){if(Math.abs(r-1f)<.01f)return "1×";if(Math.abs(r-2f)<.01f)return "2×";return String.format(Locale.US,"%.2f×",r).replace("0×","×");}

    private String currentDocKey(){return currentUri==null?"":currentUri.toString();}
    private void saveReadingPosition(){String doc=currentDocKey();if(doc.isEmpty()||homeMode)return;if(editing){saveEditorPosition();int len=Math.max(1,editor.length());reading.savePosition(doc,Math.max(0f,Math.min(1f,(float)editor.getSelectionStart()/len)));return;}preview.evaluateJavascript("(function(){var d=document.documentElement,b=document.body;var h=Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);var m=Math.max(1,h-window.innerHeight);return String(Math.max(0,Math.min(1,window.scrollY/m)));})()",value->{String raw=jsonString(value);try{reading.savePosition(doc,Float.parseFloat(raw));}catch(Exception ignored){}});}
    private void restoreReadingPosition(){String doc=currentDocKey();if(doc.isEmpty()||homeMode||editing)return;float ratio=reading.position(doc);if(ratio<=.001f)return;preview.postDelayed(()->{if(doc.equals(currentDocKey())&&!homeMode&&!editing)scrollToRatio(ratio);},280);}
    private void scrollToRatio(float ratio){float r=Math.max(0f,Math.min(1f,ratio));preview.evaluateJavascript("(function(){var d=document.documentElement,b=document.body;var h=Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);var m=Math.max(0,h-window.innerHeight);window.scrollTo(0,m*"+r+");})()",null);}
    private interface LocationDone{void done(float ratio,String label);}
    private void capturePreviewLocation(LocationDone done){String js="(function(){var d=document.documentElement,b=document.body;var h=Math.max(d?d.scrollHeight:0,b?b.scrollHeight:0);var m=Math.max(1,h-window.innerHeight);var r=Math.max(0,Math.min(1,window.scrollY/m));var els=Array.from(document.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,blockquote,td,th'));var y=window.scrollY+Math.min(120,window.innerHeight*.25);var hit='';for(var i=0;i<els.length;i++){var q=els[i].getBoundingClientRect();var bottom=q.bottom+window.scrollY;if(bottom>=y){hit=(els[i].innerText||'').trim();break;}}return JSON.stringify([r,hit.slice(0,120)]);})()";preview.evaluateJavascript(js,value->{String raw=jsonString(value);try{JSONArray a=new JSONArray(raw);float r=(float)a.optDouble(0,0d);String label=a.optString(1,"");runOnUiThread(()->done.done(r,label));}catch(Exception e){runOnUiThread(()->done.done(reading.position(currentDocKey()),""));}});}
    private void addBookmark(){String doc=currentDocKey();if(doc.isEmpty()){message("احفظ الملف أولًا","العلامات المرجعية تحتاج ملفًا محفوظًا حتى ترتبط به بشكل ثابت.");return;}if(editing){int[] range=currentParagraphRange();String t=txt();String label=range[1]>range[0]?MarkdownSpeechPlan.label(t.substring(range[0],range[1])):"";float ratio=editor.length()==0?0f:(float)editor.getSelectionStart()/editor.length();reading.savePosition(doc,ratio);ReadingStore.Bookmark b=reading.addBookmark(doc,ratio,label);Toast.makeText(this,b==null?"تعذر حفظ العلامة":"تمت إضافة العلامة المرجعية",Toast.LENGTH_SHORT).show();return;}capturePreviewLocation((ratio,label)->{reading.savePosition(doc,ratio);ReadingStore.Bookmark b=reading.addBookmark(doc,ratio,label);Toast.makeText(this,b==null?"تعذر حفظ العلامة":"تمت إضافة العلامة المرجعية",Toast.LENGTH_SHORT).show();});}
    private void bookmarks(){String doc=currentDocKey();if(doc.isEmpty()){message("لا يوجد ملف محفوظ","احفظ الملف أولًا لاستخدام العلامات المرجعية.");return;}List<ReadingStore.Bookmark> all=reading.bookmarks(doc);if(all.isEmpty()){message("العلامات المرجعية","لا توجد علامات في هذا المستند بعد.");return;}Dialog d=dialog();LinearLayout p=panel("العلامات المرجعية");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);for(ReadingStore.Bookmark b:all){LinearLayout row=new LinearLayout(this);TextView go=sheetButton(b.label+"  •  "+Math.round(b.ratio*100f)+"%",false);go.setOnClickListener(v->dismissSheet(d,p,()->jumpBookmark(b.ratio)));TextView del=mini("حذف",false);del.setOnClickListener(v->{reading.removeBookmark(doc,b.id);dismissSheet(d,p,this::bookmarks);});row.addView(go,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(72),dp(50));dl.setMarginStart(dp(6));row.addView(del,dl);LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.bottomMargin=dp(6);list.addView(row,rl);}p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());showDialog(d,p,true);}
    private void jumpBookmark(float ratio){reading.savePosition(currentDocKey(),ratio);if(editing)setEditing(false);render();preview.postDelayed(()->scrollToRatio(ratio),180);}

    private void translationMenu(){
        List<Action>a=new ArrayList<>();
        if(editing){
            if(editor.getSelectionStart()!=editor.getSelectionEnd())a.add(new Action("ترجمة المحدد",()->chooseTarget("ترجمة المحدد",toAr->translateEditorRange(selectedRange(),toAr,"المحدد")),false));
            a.add(new Action("ترجمة السطر الحالي",()->chooseTarget("ترجمة السطر الحالي",toAr->translateEditorRange(currentLineRange(),toAr,"السطر")),false));
            a.add(new Action("ترجمة الفقرة الحالية",()->chooseTarget("ترجمة الفقرة الحالية",toAr->translateEditorRange(currentParagraphRange(),toAr,"الفقرة")),false));
        }else{
            a.add(new Action("ترجمة النص المحدد في المعاينة",this::translatePreviewSelection,false));
        }
        a.add(new Action("ترجمة المستند كاملًا",()->chooseTarget("ترجمة المستند",this::translateWholeDocument),false));
        a.add(new Action("إعدادات الترجمة",this::translationSettings,false));
        a.add(new Action("إلغاء",null,false));
        sheet("الترجمة",a.toArray(new Action[0]));
    }

    private interface TargetDone{void target(boolean toArabic);}
    private interface TDone{void done(String t);}
    private void chooseTarget(String title,TargetDone done){sheet(title,new Action("إلى العربية",()->done.target(true),false),new Action("إلى الإنجليزية",()->done.target(false),false),new Action("إلغاء",null,false));}

    private int[] selectedRange(){return new int[]{Math.min(editor.getSelectionStart(),editor.getSelectionEnd()),Math.max(editor.getSelectionStart(),editor.getSelectionEnd())};}
    private int[] currentLineRange(){String t=txt();int c=Math.max(0,Math.min(editor.getSelectionStart(),t.length()));int a=t.lastIndexOf('\n',Math.max(0,c-1));a=a<0?0:a+1;int b=t.indexOf('\n',c);if(b<0)b=t.length();return new int[]{a,b};}
    private int[] currentParagraphRange(){String t=txt();int c=Math.max(0,Math.min(editor.getSelectionStart(),t.length()));int a=t.lastIndexOf("\n\n",Math.max(0,c-1));a=a<0?0:a+2;int b=t.indexOf("\n\n",c);if(b<0)b=t.length();while(a<b&&Character.isWhitespace(t.charAt(a))&&t.charAt(a)!='\n')a++;while(b>a&&Character.isWhitespace(t.charAt(b-1))&&t.charAt(b-1)!='\n')b--;return new int[]{a,b};}

    private void translateEditorRange(int[] range,boolean toAr,String kind){if(range==null||range.length<2)return;int a=Math.max(0,Math.min(range[0],txt().length())),b=Math.max(a,Math.min(range[1],txt().length()));if(b<=a){Toast.makeText(this,"لا يوجد نص في "+kind,Toast.LENGTH_SHORT).show();return;}String source=txt().substring(a,b);translate(source,toAr,t->showTranslationResult("ترجمة "+kind,t,()->replaceRange(a,b,t),null));}
    private void replaceRange(int a,int b,String translated){String old=txt();int aa=Math.max(0,Math.min(a,old.length())),bb=Math.max(aa,Math.min(b,old.length()));String next=old.substring(0,aa)+translated+old.substring(bb);history.checkpoint(old);suppress=true;editor.setText(next);editor.setSelection(aa,Math.min(next.length(),aa+translated.length()));suppress=false;history.checkpoint(next);dirty=!next.equals(savedText);scheduleDraft(next);updateTitle();}

    private void translatePreviewSelection(){preview.evaluateJavascript("(function(){return window.getSelection?window.getSelection().toString():'';})()",value->{String selected=jsonString(value);runOnUiThread(()->{if(selected.trim().isEmpty()){Toast.makeText(this,"حدد النص أولًا في وضع المعاينة",Toast.LENGTH_SHORT).show();return;}chooseTarget("ترجمة المحدد",toAr->translate(selected,toAr,t->showTranslationResult("ترجمة المحدد",t,null,null)));});});}
    private String jsonString(String value){try{return new JSONArray("["+(value==null?"null":value)+"]").optString(0,"");}catch(Exception e){return "";}}

    private void translateWholeDocument(boolean toAr){String source=txt();translate(source,toAr,t->showTranslationResult("ترجمة المستند",t,()->replaceWhole(t),()->createTranslatedCopy(t,toAr)));}
    private void replaceWhole(String t){String old=txt();history.checkpoint(old);setText(t,false);dirty=true;scheduleDraft(t);showDocument();setEditing(true);}
    private void createTranslatedCopy(String t,boolean toAr){currentUri=null;currentName=stripMd(currentName)+(toAr?"_AR.md":"_EN.md");setText(t,false);dirty=true;scheduleDraft(t);showDocument();setEditing(false);Toast.makeText(this,"تم إنشاء نسخة مترجمة غير محفوظة",Toast.LENGTH_LONG).show();}

    private void showTranslationResult(String titleText,String translated,Runnable replace,Runnable createCopy){Dialog d=dialog();LinearLayout p=panel(titleText);TextView out=label(translated,16,text,false);out.setTextIsSelectable(true);out.setPadding(dp(12),dp(12),dp(12),dp(12));out.setBackground(round(bg,border,12));ScrollView sc=new ScrollView(this);sc.addView(out,new ScrollView.LayoutParams(-1,-2));p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));if(replace!=null){TextView b=sheetButton("استبدال النص بالترجمة",false);b.setOnClickListener(v->dismissSheet(d,p,replace));p.addView(b,buttonLp());}if(createCopy!=null){TextView b=sheetButton("إنشاء نسخة مترجمة",false);b.setOnClickListener(v->dismissSheet(d,p,createCopy));p.addView(b,buttonLp());}TextView copy=sheetButton("نسخ الترجمة",false);copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("translation",translated));Toast.makeText(this,"تم نسخ الترجمة",Toast.LENGTH_SHORT).show();});p.addView(copy,buttonLp());TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());showDialog(d,p,true);}

    private void translationSettings(){
        Dialog d=dialog();LinearLayout p=panel("إعدادات الترجمة");
        String engine=prefs.getString(PREF_TRANSLATION_ENGINE,"ai");
        String provider=AiTranslationService.normalizeProvider(prefs.getString(AiTranslationService.PREF_PROVIDER,AiTranslationService.PROVIDER_OPENAI));
        final String[] selectedEngine={engine},selectedProvider={provider};
        TextView engineLabel=label("المحرك الحالي: "+("local".equals(engine)?"ترجمة محلية":"ذكاء اصطناعي — "+AiTranslationService.providerLabel(provider)),14,muted,false);p.addView(engineLabel,textLp());
        LinearLayout er=new LinearLayout(this);TextView ai=mini("AI",!"local".equals(engine)),local=mini("محلي","local".equals(engine));er.addView(ai,weight(1,0,4));er.addView(local,weight(1,4,0));p.addView(er,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView providerLabel=label("مزود الذكاء الاصطناعي",13,muted,false);p.addView(providerLabel,textLp());
        LinearLayout pr=new LinearLayout(this);TextView openai=mini("OpenAI",AiTranslationService.PROVIDER_OPENAI.equals(provider)),deepseek=mini("DeepSeek",AiTranslationService.PROVIDER_DEEPSEEK.equals(provider));pr.addView(openai,weight(1,0,4));pr.addView(deepseek,weight(1,4,0));p.addView(pr,new LinearLayout.LayoutParams(-1,dp(48)));
        EditText endpoint=inputField("عنوان API",aiEndpoint(provider),false);EditText model=inputField("اسم النموذج",aiModel(provider),false);EditText key=inputField(secrets.hasKey(provider)?"API Key محفوظ — اتركه فارغًا للإبقاء عليه":"API Key","",true);p.addView(endpoint,textLp());p.addView(model,textLp());p.addView(key,textLp());
        TextView note=label("لكل مزود مفتاح مستقل، ويُشفّر على الجهاز بواسطة Android Keystore ولا يُكتب في السجلات.",12,muted,false);p.addView(note,textLp());
        Runnable refresh=()->{String pv=selectedProvider[0];boolean ds=AiTranslationService.PROVIDER_DEEPSEEK.equals(pv);openai.setBackground(round(ds?bg:accent,ds?border:accent,10));deepseek.setBackground(round(ds?accent:bg,ds?accent:border,10));endpoint.setText(aiEndpoint(pv));model.setText(aiModel(pv));key.setText("");key.setHint(secrets.hasKey(pv)?"API Key محفوظ — اتركه فارغًا للإبقاء عليه":"API Key");engineLabel.setText("المحرك الحالي: "+("local".equals(selectedEngine[0])?"ترجمة محلية":"ذكاء اصطناعي — "+AiTranslationService.providerLabel(pv)));};
        ai.setOnClickListener(v->{selectedEngine[0]="ai";ai.setBackground(round(accent,accent,10));local.setBackground(round(bg,border,10));engineLabel.setText("المحرك الحالي: ذكاء اصطناعي — "+AiTranslationService.providerLabel(selectedProvider[0]));});
        local.setOnClickListener(v->{selectedEngine[0]="local";local.setBackground(round(accent,accent,10));ai.setBackground(round(bg,border,10));engineLabel.setText("المحرك الحالي: ترجمة محلية");});
        openai.setOnClickListener(v->{selectedProvider[0]=AiTranslationService.PROVIDER_OPENAI;refresh.run();});deepseek.setOnClickListener(v->{selectedProvider[0]=AiTranslationService.PROVIDER_DEEPSEEK;refresh.run();});
        TextView test=sheetButton("اختبار الاتصال",false);p.addView(test,buttonLp());TextView save=sheetButton("حفظ الإعدادات",false);p.addView(save,buttonLp());TextView clear=sheetButton("حذف API Key المحفوظ لهذا المزود",true);p.addView(clear,buttonLp());TextView close=sheetButton("إغلاق",false);p.addView(close,buttonLp());
        test.setOnClickListener(v->{try{String pv=selectedProvider[0],entered=key.getText().toString().trim();if(!entered.isEmpty())secrets.save(pv,entered);saveAiConfig(pv,endpoint.getText().toString().trim(),model.getText().toString().trim());prefs.edit().putString(AiTranslationService.PREF_PROVIDER,pv).apply();}catch(Exception e){error("تعذر تجهيز الاختبار",e);return;}test.setEnabled(false);test.setText("جاري اختبار "+AiTranslationService.providerLabel(selectedProvider[0])+"…");aiTranslator.translateMarkdown("Hello",true,new TranslationService.Callback(){public void onStatus(String st){}public void onProgress(int c,int total){}public void onSuccess(String t){runOnUiThread(()->{test.setEnabled(true);test.setText("✓ الاتصال يعمل — "+AiTranslationService.providerLabel(selectedProvider[0]));});}public void onError(Exception e){runOnUiThread(()->{test.setEnabled(true);test.setText("اختبار الاتصال");error("فشل اختبار "+AiTranslationService.providerLabel(selectedProvider[0]),e);});}});});
        save.setOnClickListener(v->{try{String pv=selectedProvider[0],entered=key.getText().toString().trim();if(!entered.isEmpty())secrets.save(pv,entered);saveAiConfig(pv,endpoint.getText().toString().trim(),model.getText().toString().trim());prefs.edit().putString(PREF_TRANSLATION_ENGINE,selectedEngine[0]).putString(AiTranslationService.PREF_PROVIDER,pv).apply();Toast.makeText(this,"تم حفظ إعدادات الترجمة",Toast.LENGTH_SHORT).show();dismissSheet(d,p,null);}catch(Exception e){error("تعذر حفظ API Key",e);}});
        clear.setOnClickListener(v->{String pv=selectedProvider[0];secrets.clear(pv);key.setText("");key.setHint("API Key");Toast.makeText(this,"تم حذف مفتاح "+AiTranslationService.providerLabel(pv),Toast.LENGTH_SHORT).show();});close.setOnClickListener(v->dismissSheet(d,p,null));showDialog(d,p,true);
    }

    private String aiEndpoint(String provider){return aiTranslator.configuredEndpoint(provider);}
    private String aiModel(String provider){return aiTranslator.configuredModel(provider);}
    private void saveAiConfig(String provider,String endpoint,String model){String pv=AiTranslationService.normalizeProvider(provider);String ep=endpoint==null||endpoint.trim().isEmpty()?AiTranslationService.defaultEndpoint(pv):endpoint.trim();String md=model==null||model.trim().isEmpty()?AiTranslationService.defaultModel(pv):model.trim();prefs.edit().putString(AiTranslationService.endpointPref(pv),ep).putString(AiTranslationService.modelPref(pv),md).apply();}

    private EditText inputField(String hint,String value,boolean secret){
        EditText e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_NORMAL));ReaderUi.field(e,palette);return e;
    }

    private void translate(String source,boolean toAr,TDone done){if(source==null||source.trim().isEmpty())return;String engine=prefs.getString(PREF_TRANSLATION_ENGINE,"ai");if("local".equals(engine))translateLocal(source,toAr,done);else translateAi(source,toAr,done);}
    private void translateAi(String source,boolean toAr,TDone done){String provider=aiTranslator.currentProvider();if(!secrets.hasKey(provider)){choice("مفتاح API غير موجود","أضف مفتاح "+AiTranslationService.providerLabel(provider)+" أولًا أو استخدم الترجمة المحلية.",new Action("إعدادات الترجمة",this::translationSettings,false),new Action("استخدام المحلي الآن",()->translateLocal(source,toAr,done),false),new Action("إلغاء",null,false));return;}Dialog d=dialog();LinearLayout p=panel("جاري الترجمة بالذكاء الاصطناعي");TextView status=label("الاتصال بخدمة AI…",14,muted,false);p.addView(status,textLp());TextView hide=sheetButton("إخفاء",false);hide.setOnClickListener(v->dismissSheet(d,p,null));p.addView(hide,buttonLp());showDialog(d,p,false);aiTranslator.translateMarkdown(source,toAr,new TranslationService.Callback(){public void onStatus(String st){runOnUiThread(()->status.setText(st));}public void onProgress(int c,int total){runOnUiThread(()->status.setText("ترجمة "+c+" من "+total+"…"));}public void onSuccess(String t){runOnUiThread(()->{if(d.isShowing())dismissSheet(d,p,()->done.done(t));else done.done(t);});}public void onError(Exception e){runOnUiThread(()->{Runnable show=()->choice("تعذر إكمال ترجمة AI",e.getMessage()==null?"حدث خطأ أثناء الاتصال":e.getMessage(),new Action("المحاولة محليًا",()->translateLocal(source,toAr,done),false),new Action("إعدادات الترجمة",MainActivity.this::translationSettings,false),new Action("إغلاق",null,false));if(d.isShowing())dismissSheet(d,p,show);else show.run();});}});}
    private void translateLocal(String source,boolean toAr,TDone done){Dialog d=dialog();LinearLayout p=panel("الترجمة المحلية");TextView status=label("جاري التحقق من نموذج اللغة…",14,muted,false);p.addView(status,textLp());p.addView(label("لن يبقى التطبيق في حالة انتظار مفتوحة؛ إذا تعذر تجهيز النموذج ستظهر رسالة خطأ واضحة.",12,muted,false),textLp());TextView hide=sheetButton("إخفاء",false);hide.setOnClickListener(v->dismissSheet(d,p,null));p.addView(hide,buttonLp());showDialog(d,p,false);translator.translateMarkdown(source,toAr,new TranslationService.Callback(){public void onStatus(String st){runOnUiThread(()->status.setText(st));}public void onProgress(int c,int total){runOnUiThread(()->status.setText("ترجمة "+c+" من "+total+"…"));}public void onSuccess(String t){runOnUiThread(()->{Runnable doneRun=()->{if(t.equals(source))Toast.makeText(MainActivity.this,"لا يوجد نص مناسب للترجمة",Toast.LENGTH_SHORT).show();else done.done(t);};if(d.isShowing())dismissSheet(d,p,doneRun);else doneRun.run();});}public void onError(Exception e){runOnUiThread(()->{Runnable show=()->choice("تعذر إكمال الترجمة المحلية",e.getMessage()==null?"فشلت الترجمة المحلية":e.getMessage(),new Action("استخدام AI",()->translateAi(source,toAr,done),false),new Action("إعادة المحاولة",()->translateLocal(source,toAr,done),false),new Action("إغلاق",null,false));if(d.isShowing())dismissSheet(d,p,show);else show.run();});}});}

    private void toggleSearch(){if(homeMode)return;if(searchBar.getVisibility()==View.VISIBLE)closeSearch();else openSearch(false);}
    private void openSearch(boolean replace){
        if(homeMode)return;if(replace&&!editing)setEditing(true);findPanel.setEditorMode(editing);findPanel.setExpanded(replace);
        searchBar.setVisibility(View.VISIBLE);searchInput.requestFocus();updateWorkspaceChrome();keyboard(searchInput);runSearch(searchInput.getText().toString());
    }

    private void closeSearch(){
        if(searchBar==null)return;if(searchPending!=null){main.removeCallbacks(searchPending);searchPending=null;}
        searchBar.setVisibility(View.GONE);searchInput.clearFocus();findPanel.replacement.clearFocus();clearSearch();hideKeyboard();updateWorkspaceChrome();
    }
    private void runSearch(String q){
        if(searchPending!=null){main.removeCallbacks(searchPending);searchPending=null;}
        if(searchBar==null||searchBar.getVisibility()!=View.VISIBLE)return;
        if(q==null||q.isEmpty()){clearSearch();return;}
        searchPending=()->{searchPending=null;if(searchBar.getVisibility()!=View.VISIBLE)return;
            if(editing){SearchMatchState result=SearchMatchState.scan(txt(),q,findPanel.matchesCase(),Math.max(0,editor.getSelectionStart()),1);showSearchMatch(result,q);}
            else preview.findAllAsync(q);
        };main.postDelayed(searchPending,160);
    }
    private void find(boolean forward){
        if(searchPending!=null){main.removeCallbacks(searchPending);searchPending=null;}
        String q=searchInput.getText().toString();if(q.isEmpty())return;
        hideKeyboard(); // navigating is a reading action; the query retains focus and can be tapped to edit
        if(!editing){preview.findNext(forward);return;}selectSearchMatch(q,findPanel.matchesCase(),forward);
    }
    private void selectSearchMatch(String q,boolean matchCase,boolean forward){
        if(q==null||q.isEmpty())return;
        int anchor=forward?Math.max(0,editor.getSelectionEnd()):Math.max(0,editor.getSelectionStart());
        SearchMatchState result=SearchMatchState.scan(txt(),q,matchCase,anchor,forward?1:-1);showSearchMatch(result,q);
    }
    private void showSearchMatch(SearchMatchState result,String query){
        clearEditorHighlight();findPanel.setResults(result.ordinal,result.count);
        if(result.offset<0)return;
        editor.setSelection(result.offset,Math.min(editor.length(),result.offset+query.length()));
        activeSearchHighlight=new BackgroundColorSpan(UiPalette.alpha(accent,dark?95:70));
        editor.getText().setSpan(activeSearchHighlight,result.offset,result.offset+query.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ensureSearchVisible();
    }
    private void ensureSearchVisible(){
        editor.post(()->{
            if(!editing||searchBar.getVisibility()!=View.VISIBLE||editor.getLayout()==null)return;
            int offset=Math.max(0,editor.getSelectionStart());android.text.Layout layout=editor.getLayout();int line=layout.getLineForOffset(offset);
            int visible=Math.max(1,editor.getHeight()-editor.getTotalPaddingTop()-editor.getTotalPaddingBottom());
            int target=Math.max(0,layout.getLineTop(line)-visible/3);int max=Math.max(0,layout.getHeight()-visible);
            editor.scrollTo(0,Math.min(target,max));
        });
    }
    private void clearEditorHighlight(){if(activeSearchHighlight!=null&&editor!=null){editor.getText().removeSpan(activeSearchHighlight);activeSearchHighlight=null;}}
    private void editorSearch(String q){
        clearEditorHighlight();SearchMatchState result=SearchMatchState.scan(txt(),q,findPanel.matchesCase(),Math.max(0,editor.getSelectionStart()),0);findPanel.setResults(result.ordinal,result.count);
    }
    private void clearSearch(){if(findPanel!=null)findPanel.setResults(0,0);clearEditorHighlight();if(preview!=null)preview.clearMatches();}

    private void editorScrollSettings(){
        Dialog d=dialog();LinearLayout p=panel("سرعة تمرير التحرير");
        TextView info=label("هذه النسبة تتحكم بسرعة الانزلاق بعد رفع إصبعك. اسحب ثم ارفع إصبعك ليستمر المحرر بالتمرير، والمس الشاشة مرة أخرى لإيقافه فورًا.",13,muted,false);p.addView(info,textLp());
        TextView value=label(editorScrollPercent+"%",24,text,true);value.setGravity(Gravity.CENTER);p.addView(value,new LinearLayout.LayoutParams(-1,dp(48)));
        SeekBar speed=new SeekBar(this);speed.setMax(EDITOR_SCROLL_MAX-EDITOR_SCROLL_MIN);speed.setProgress(editorScrollPercent-EDITOR_SCROLL_MIN);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int x,boolean fromUser){editorScrollPercent=EDITOR_SCROLL_MIN+x;value.setText(editorScrollPercent+"%");editor.setFlingSpeedPercent(editorScrollPercent);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();}});
        p.addView(speed,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout row=new LinearLayout(this);TextView reset=mini("إعادة إلى 100%",false),done=mini("تم",true);reset.setOnClickListener(v->{editorScrollPercent=EDITOR_SCROLL_DEFAULT;speed.setProgress(EDITOR_SCROLL_DEFAULT-EDITOR_SCROLL_MIN);prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();});done.setOnClickListener(v->{prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();dismissSheet(d,p,null);});row.addView(reset,weight(1,0,4));row.addView(done,weight(1,4,0));p.addView(row,new LinearLayout.LayoutParams(-1,-2));
        showDialog(d,p,false);
    }

    private EditText multiLineField(String hint,String value,int minLines){
        EditText e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(Math.max(1,minLines));e.setMaxLines(4);ReaderUi.field(e,palette);return e;
    }

    private void searchReplaceDialog(){openSearch(true);}

    private void replaceInline(boolean all){
        String query=searchInput.getText().toString(),replacement=findPanel.replacement.getText().toString();boolean matchCase=findPanel.matchesCase();
        if(!editing||query.isEmpty())return;
        if(searchPending!=null){main.removeCallbacks(searchPending);searchPending=null;}
        if(all){
            int count=SearchReplaceEngine.count(txt(),query,matchCase);if(count==0)return;
            choice("استبدال جميع النتائج؟","سيتم استبدال "+count+" نتيجة. يمكنك التراجع عن العملية من أدوات التحرير.",
                new Action("استبدال الكل",()->{replaceAllSearch(query,replacement,matchCase);runSearch(query);},false),new Action("إلغاء",null,false));
        }else{replaceCurrentSearch(query,replacement,matchCase);selectSearchMatch(query,matchCase,true);}
    }

    private int replaceCurrentSearch(String q,String replacement,boolean matchCase){
        if(q==null||q.isEmpty())return 0;String source=txt();int a=Math.min(editor.getSelectionStart(),editor.getSelectionEnd()),b=Math.max(editor.getSelectionStart(),editor.getSelectionEnd());
        int idx=(b-a==q.length()&&SearchReplaceEngine.matchesAt(source,q,a,matchCase))?a:SearchReplaceEngine.findNext(source,q,b,matchCase);
        if(idx<0)idx=SearchReplaceEngine.findNext(source,q,0,matchCase);if(idx<0){Toast.makeText(this,"لا توجد نتيجة للاستبدال",Toast.LENGTH_SHORT).show();return 0;}
        String next=source.substring(0,idx)+replacement+source.substring(idx+q.length());history.checkpoint(source);suppress=true;editor.setText(next);int caret=Math.min(next.length(),idx+replacement.length());editor.setSelection(caret);suppress=false;history.checkpoint(next);dirty=!next.equals(savedText);scheduleDraft(next);updateTitle();editor.bringPointIntoView(caret);return 1;
    }

    private int replaceAllSearch(String q,String replacement,boolean matchCase){
        if(q==null||q.isEmpty())return 0;String source=txt();SearchReplaceEngine.ReplaceResult result=SearchReplaceEngine.replaceAll(source,q,replacement,matchCase);if(result.count<=0){Toast.makeText(this,"لا توجد نتائج للاستبدال",Toast.LENGTH_SHORT).show();return 0;}
        int oldCaret=Math.max(0,editor.getSelectionStart());history.checkpoint(source);suppress=true;editor.setText(result.text);editor.setSelection(Math.min(oldCaret,result.text.length()));suppress=false;history.checkpoint(result.text);dirty=!result.text.equals(savedText);scheduleDraft(result.text);updateTitle();Toast.makeText(this,"تم استبدال "+result.count+" نتيجة",Toast.LENGTH_SHORT).show();return result.count;
    }

    private void heading(int n){apply(MarkdownTransforms.heading(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),n));}
    private void prefix(String p,boolean numbered){apply(MarkdownTransforms.prefixLines(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),p,numbered));}
    private void wrap(String a,String b,String ph){apply(MarkdownTransforms.wrap(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),a,b,ph));}
    private void insert(String block){apply(MarkdownTransforms.insert(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),block));}
    private void apply(MarkdownTransforms.Result r){String before=txt();history.checkpoint(before);suppress=true;editor.setText(r.text);editor.setSelection(r.selectionStart,r.selectionEnd);suppress=false;history.checkpoint(r.text);dirty=!r.text.equals(savedText);scheduleDraft(r.text);updateTitle();}
    private void undo(){String t=history.undo(txt());if(t!=null)historyText(t);}private void redo(){String t=history.redo(txt());if(t!=null)historyText(t);}private void historyText(String t){suppress=true;editor.setText(t);editor.setSelection(t.length());suppress=false;dirty=!t.equals(savedText);scheduleDraft(t);updateTitle();}
    private void scheduleDraft(String t){if(draftPending!=null)main.removeCallbacks(draftPending);if(!dirty){drafts.clear();return;}String n=currentName,u=currentUri==null?"":currentUri.toString();draftPending=()->io.execute(()->{try{drafts.save(t,n,u);}catch(Exception ignored){}});main.postDelayed(draftPending,850);}

    private void confirm(Runnable go){if(!dirty){go.run();return;}choice("تغييرات غير محفوظة","هل تريد حفظ التغييرات قبل المتابعة؟",new Action("حفظ",()->save(go),false),new Action("تجاهل التغييرات",()->{drafts.clear();dirty=false;go.run();},true),new Action("إلغاء",null,false));}
    @Override protected void onPause(){saveReadingPosition();super.onPause();}
    @Override public void onBackPressed(){if(searchBar.getVisibility()==View.VISIBLE){closeSearch();return;}if(!homeMode){confirm(this::showHome);return;}if(homePage!=0){switchHomePage(0);return;}super.onBackPressed();}
    private void updateTitle(){if(title==null)return;title.setText(homeMode?"MD Reader":(dirty?"• ":"")+currentName);}
    private void error(String t,Exception e){String m=e==null?"حدث خطأ غير معروف":e.getMessage();if(m==null||m.isEmpty())m=e==null?"Error":e.getClass().getSimpleName();message(t,m);}

    private static final class Action{final String text;final Runnable run;final boolean danger;Action(String t,Runnable r,boolean d){text=t;run=r;danger=d;}}
    private void sheet(String t,Action...aa){
        Dialog d=dialog();LinearLayout p=panel(t);
        if(aa!=null)for(Action a:aa){TextView b=sheetButton(a.text,a.danger);b.setOnClickListener(v->dismissSheet(d,p,a.run));p.addView(b,buttonLp());}
        showDialog(d,p,true);
    }
    private void choice(String t,String m,Action...aa){Dialog d=dialog();LinearLayout p=panel(t);p.addView(label(m,15,muted,false),textLp());for(Action a:aa){TextView b=sheetButton(a.text,a.danger);b.setOnClickListener(v->dismissSheet(d,p,a.run));p.addView(b,buttonLp());}showDialog(d,p,false);}
    private void message(String t,String m){choice(t,m,new Action("حسنًا",null,false));}
    private Dialog dialog(){Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);return d;}
    private LinearLayout panel(String t){return ResponsiveSheet.panel(this,t,palette);}
    private void showDialog(Dialog d,View p,boolean tall){ResponsiveSheet.show(d,(LinearLayout)p,palette);}
    private void dismissSheet(Dialog d,View p,Runnable after){
        if(d!=null&&d.isShowing())d.dismiss();if(after!=null)after.run();
    }
    private TextView sheetButton(String s,boolean danger){
        TextView v=ReaderUi.text(this,ReaderUi.cleanLabel(s),15,danger?palette.danger:text,false);v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setMinHeight(dp(48));ReaderUi.accessibleAction(v);
        v.setCompoundDrawablesRelative(new UiIcon(ReaderUi.kindFor(s),danger?palette.danger:muted,dp(22)),null,null,null);v.setCompoundDrawablePadding(dp(12));
        v.setBackground(ReaderUi.ripple(this,palette.raised,Color.TRANSPARENT,12,accent));return v;
    }

    private TextView topAction(String s,int size){TextView v=label(s,size,text,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setMinWidth(dp(48));v.setClickable(true);return v;}
    private TextView small(String s){TextView v=topAction(s,14);v.setLayoutParams(new LinearLayout.LayoutParams(-2,-1));return v;}
    private TextView tab(String s){TextView v=ReaderUi.button(this,s,false,palette);v.setPadding(dp(8),dp(8),dp(8),dp(8));ReaderUi.accessibleAction(v);return v;}
    private void format(String s,View.OnClickListener l){
        TextView v=label(s,14,text,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),0,dp(12),0);v.setMinWidth(dp(48));ReaderUi.accessibleAction(v);
        v.setContentDescription(formatLabel(s));v.setTooltipText(formatLabel(s));v.setBackground(ReaderUi.ripple(this,0,0,10,accent));v.setOnClickListener(l);formatInner.addView(v,new LinearLayout.LayoutParams(-2,-1));
    }
    private String formatLabel(String s){
        switch(s){case "↶":return "تراجع";case "↷":return "إعادة";case "B":return "غامق";case "I":return "مائل";case "S":return "يتوسطه خط";case "` `":return "كود داخل السطر";case "```":return "حاوية كود";case "❝":return "اقتباس";case "•":return "قائمة نقطية";case "1.":return "قائمة مرقمة";case "☑":return "قائمة مهام";case "—":return "فاصل أفقي";default:return s.startsWith("H")?"عنوان المستوى "+s.substring(1):s;}
    }
    private void divider(){View v=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(1),dp(26));lp.setMargins(dp(4),0,dp(4),0);v.setTag(Boolean.TRUE);formatInner.addView(v,lp);}
    private TextView label(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setGravity(Gravity.START);return v;}
    private TextView homeButton(String s,boolean primary){TextView v=ReaderUi.button(this,s,primary,palette);ReaderUi.accessibleAction(v);return v;}
    private TextView mini(String s,boolean primary){TextView v=homeButton(s,primary);v.setTextSize(14);return v;}
    private LinearLayout card(){LinearLayout c=ReaderUi.column(this);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(ReaderUi.shape(this,surface,border,16));return c;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);return p;}private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);return p;}private LinearLayout.LayoutParams textLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);p.bottomMargin=dp(8);return p;}private LinearLayout.LayoutParams weight(float w,int st,int en){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,w);p.setMarginStart(dp(st));p.setMarginEnd(dp(en));return p;}
    private GradientDrawable round(int fill,int stroke,int r){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));d.setStroke(dp(1),stroke);return d;}
    private void tint(ViewGroup g){for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i) instanceof TextView&&g.getChildAt(i)!=title&&g.getChildAt(i)!=searchInput)((TextView)g.getChildAt(i)).setTextColor(text);}
    private void keyboard(View v){v.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT),100);}private void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    private void external(String s){try{Uri u=Uri.parse(s);String sc=u.getScheme();if(sc!=null&&(sc.equals("http")||sc.equals("https")||sc.equals("mailto")||sc.equals("tel")))startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}}
    private String txt(){return editor==null?"":editor.getText().toString();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}private static String ensureMd(String n){if(n==null||n.isEmpty())n="document.md";String l=n.toLowerCase(Locale.ROOT);return l.endsWith(".md")||l.endsWith(".markdown")?n:n+".md";}private static String stripMd(String n){return n==null?"Markdown":n.replaceFirst("(?i)\\.(md|markdown)$","");}private static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}private String time(long t){return t<=0?"":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t));}

    @Override protected void onDestroy(){if(searchPending!=null)main.removeCallbacks(searchPending);if(draftPending!=null)main.removeCallbacks(draftPending);if(renderPending!=null)main.removeCallbacks(renderPending);if(editorMaintenancePending!=null)main.removeCallbacks(editorMaintenancePending);renderToken++;previewIo.shutdownNow();io.shutdownNow();if(speech!=null)speech.shutdown();aiTranslator.shutdown();history.cancel();if(preview!=null){preview.removeJavascriptInterface("Android");preview.destroy();}super.onDestroy();}
    private class Bridge{@JavascriptInterface public void copyText(String s){runOnUiThread(()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("code",s==null?"":s));Toast.makeText(MainActivity.this,"تم النسخ",Toast.LENGTH_SHORT).show();});}@JavascriptInterface public void openLink(String s){runOnUiThread(()->external(s));}}
    private abstract static class Watcher implements TextWatcher{public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}}
    private class History{final List<String> undo=new ArrayList<>(),redo=new ArrayList<>();String cur="";Runnable pending;void reset(String s){cancel();undo.clear();redo.clear();cur=s==null?"":s;}void schedule(String s){cancel();pending=()->checkpoint(s);main.postDelayed(pending,450);}void checkpoint(String s){cancel();if(s==null)s="";if(s.equals(cur))return;undo.add(cur);int limit=s.length()>=LARGE_EDITOR_DEBOUNCE_CHARS?LARGE_HISTORY_LIMIT:80;while(undo.size()>limit)undo.remove(0);cur=s;redo.clear();}String undo(String actual){cancel();if(!actual.equals(cur))checkpoint(actual);if(undo.isEmpty())return null;redo.add(cur);cur=undo.remove(undo.size()-1);return cur;}String redo(String actual){cancel();if(!actual.equals(cur))checkpoint(actual);if(redo.isEmpty())return null;undo.add(cur);cur=redo.remove(redo.size()-1);return cur;}void cancel(){if(pending!=null){main.removeCallbacks(pending);pending=null;}}}
}
