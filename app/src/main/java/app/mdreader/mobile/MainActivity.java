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
    private EditText editor,searchInput;
    private AdjustableScroller editorScroller;
    private TextView title,searchCount,previewTab,editTab,homeBtn,openBtn,saveBtn,findBtn,moreBtn,quickNavBtn,extraToolsBtn,replaceSearchBtn;
    private Uri currentUri;
    private String currentName="غير محفوظ.md",savedText="";
    private boolean dirty=false,editing=false,homeMode=true,readerReady=false,suppress=false,dark=false;
    private int fontSize=17,editorScrollPercent=EDITOR_SCROLL_DEFAULT,bg,surface,text,muted,border,accent;
    private Runnable saveThen,draftPending;
    private Dialog speechDialog;
    private TextView speechStatus,speechProgress,speechPlay,speechRate;

    @Override protected void onCreate(Bundle state){
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE); recents=new RecentStore(prefs); drafts=new DraftStore(this,prefs); reading=new ReadingStore(prefs); secrets=new ApiKeyStore(this); aiTranslator=new AiTranslationService(secrets,prefs);
        dark=prefs.getBoolean(PREF_DARK,false); fontSize=clamp(prefs.getInt(PREF_FONT,17),13,28); editorScrollPercent=clamp(prefs.getInt(PREF_EDITOR_SCROLL,EDITOR_SCROLL_DEFAULT),EDITOR_SCROLL_MIN,EDITOR_SCROLL_MAX);
        setTheme(dark?R.style.Theme_MDReader_Dark:R.style.Theme_MDReader_Light); super.onCreate(state);
        speech=new SpeechReader(this,prefs,new SpeechReader.Listener(){public void onState(SpeechReader.State st){runOnUiThread(()->updateSpeechUi(st));}public void onError(String m){runOnUiThread(()->message("تعذر تشغيل القراءة بالصوت",m));}});
        buildUi(); applyTheme(); configureEditor(); configureEditorComfort(); enhanceEditorToolbar(); configurePreview();
        Intent i=getIntent();
        if(i!=null&&Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null) load(i.getData(),i.getFlags()); else showHome();
    }

    @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); if(i!=null&&Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null) confirm(() -> load(i.getData(),i.getFlags())); }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); setContentView(root); root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(10),0,dp(6),0); root.addView(top,new LinearLayout.LayoutParams(-1,dp(56)));
        title=new TextView(this); title.setTextSize(16); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); title.setGravity(Gravity.CENTER_VERTICAL); title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        homeBtn=topAction("⌂",20); homeBtn.setOnClickListener(v->confirm(this::showHome)); top.addView(homeBtn,new LinearLayout.LayoutParams(dp(44),-1));
        openBtn=topAction("فتح",14); openBtn.setOnClickListener(v->openPicker()); top.addView(openBtn);
        saveBtn=topAction("حفظ",14); saveBtn.setOnClickListener(v->save(null)); top.addView(saveBtn);
        findBtn=topAction("بحث",14); findBtn.setOnClickListener(v->toggleSearch()); top.addView(findBtn);
        moreBtn=topAction("⋮",24); moreBtn.setOnClickListener(v->more()); top.addView(moreBtn,new LinearLayout.LayoutParams(dp(44),-1));

        searchBar=new LinearLayout(this); searchBar.setGravity(Gravity.CENTER_VERTICAL); searchBar.setPadding(dp(8),dp(5),dp(8),dp(5)); searchBar.setVisibility(View.GONE); root.addView(searchBar,new LinearLayout.LayoutParams(-1,dp(52)));
        searchInput=new EditText(this); searchInput.setSingleLine(); searchInput.setHint("بحث داخل الملف"); searchInput.setTextSize(15); searchInput.setPadding(dp(12),0,dp(12),0); searchBar.addView(searchInput,new LinearLayout.LayoutParams(0,-1,1));
        searchCount=small("0/0"); searchBar.addView(searchCount); TextView prev=small("↑"),next=small("↓"),close=small("✕"); replaceSearchBtn=small("⇄"); replaceSearchBtn.setContentDescription("بحث واستبدال"); replaceSearchBtn.setTooltipText("بحث واستبدال"); prev.setOnClickListener(v->find(false)); next.setOnClickListener(v->find(true)); replaceSearchBtn.setOnClickListener(v->searchReplaceDialog()); close.setOnClickListener(v->closeSearch()); searchBar.addView(prev);searchBar.addView(next);searchBar.addView(replaceSearchBtn);searchBar.addView(close);
        searchInput.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable s){runSearch(s.toString());}});

        frame=new FrameLayout(this); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        home=new ScrollView(this); home.setFillViewport(true); homeContent=new LinearLayout(this); homeContent.setOrientation(LinearLayout.VERTICAL); homeContent.setPadding(dp(18),dp(20),dp(18),dp(32)); home.addView(homeContent,new ScrollView.LayoutParams(-1,-2)); frame.addView(home,new FrameLayout.LayoutParams(-1,-1));
        preview=new WebView(this); frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        editor=new EditText(this); editor.setGravity(Gravity.TOP); editor.setPadding(dp(16),dp(14),dp(16),dp(32)); editor.setTextSize(16); editor.setSingleLine(false); editor.setHorizontallyScrolling(false); editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); editor.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); editor.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START); editor.setVisibility(View.GONE); frame.addView(editor,new FrameLayout.LayoutParams(-1,-1));

        formatBar=new HorizontalScrollView(this); formatBar.setHorizontalScrollBarEnabled(false); formatBar.setVisibility(View.GONE); formatInner=new LinearLayout(this); formatInner.setGravity(Gravity.CENTER_VERTICAL); formatInner.setPadding(dp(6),dp(5),dp(6),dp(5)); formatBar.addView(formatInner,new HorizontalScrollView.LayoutParams(-2,-1)); root.addView(formatBar,new LinearLayout.LayoutParams(-1,dp(50))); buildFormats();
        bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(8),dp(5),dp(8),dp(5)); root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(54)));
        previewTab=tab("معاينة"); editTab=tab("تحرير"); previewTab.setOnClickListener(v->setEditing(false)); editTab.setOnClickListener(v->setEditing(true)); bottom.addView(previewTab,new LinearLayout.LayoutParams(0,-1,1)); bottom.addView(editTab,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void buildFormats(){
        format("↶",v->undo());format("↷",v->redo());divider(); format("H1",v->heading(1));format("H2",v->heading(2));format("H3",v->heading(3));divider();
        format("B",v->wrap("**","**","نص"));format("I",v->wrap("*","*","نص"));format("S",v->wrap("~~","~~","نص"));format("` `",v->wrap("`","`","code"));format("```",v->apply(MarkdownTransforms.fencedCode(txt(),editor.getSelectionStart(),editor.getSelectionEnd())));divider();
        format("❝",v->prefix("> ",false));format("•",v->prefix("- ",false));format("1.",v->prefix("",true));format("☑",v->prefix("- [ ] ",false));divider();
        format("رابط",v->apply(MarkdownTransforms.link(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),false)));format("صورة",v->apply(MarkdownTransforms.link(txt(),editor.getSelectionStart(),editor.getSelectionEnd(),true)));format("جدول",v->insert("\n| العمود 1 | العمود 2 |\n| --- | --- |\n| قيمة 1 | قيمة 2 |\n"));format("—",v->insert("\n---\n"));divider();format("ترجمة",v->translationMenu());
    }

    private void configureEditor(){
        history.reset(""); editor.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable s){ if(suppress)return; String t=s.toString(); dirty=!t.equals(savedText); history.schedule(t); scheduleDraft(t); updateTitle(); if(searchBar.getVisibility()==View.VISIBLE) editorSearch(searchInput.getText().toString()); }});
    }


    private void configureEditorComfort(){
        editor.setScrollContainer(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setScrollbarFadingEnabled(true);
        editor.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        editor.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        editorScroller=new AdjustableScroller(this,editorScrollPercent);
        editor.setScroller(editorScroller);
        editor.setGravity(Gravity.TOP|Gravity.START);
        editor.setHorizontallyScrolling(false);
        if(Build.VERSION.SDK_INT>=23){
            editor.setScrollIndicators(View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM,
                    View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM);
        }
    }

    private void enhanceEditorToolbar(){
        if(formatInner==null)return;
        quickNavBtn=mini("⇅",false);
        quickNavBtn.setTextSize(21);
        quickNavBtn.setGravity(Gravity.CENTER);
        quickNavBtn.setContentDescription("التنقل السريع داخل المستند");
        quickNavBtn.setTooltipText("التنقل السريع");
        quickNavBtn.setOnClickListener(v->documentNavigation());

        extraToolsBtn=mini("＋",false);
        extraToolsBtn.setTextSize(23);
        extraToolsBtn.setGravity(Gravity.CENTER);
        extraToolsBtn.setContentDescription("أدوات Markdown إضافية");
        extraToolsBtn.setTooltipText("أدوات إضافية");
        extraToolsBtn.setOnClickListener(v->extraMarkdownTools());

        LinearLayout.LayoutParams navLp=new LinearLayout.LayoutParams(dp(48),dp(42));
        navLp.setMarginEnd(dp(2));
        LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(dp(48),dp(42));
        toolsLp.setMarginEnd(dp(2));
        formatInner.addView(extraToolsBtn,0,toolsLp);
        formatInner.addView(quickNavBtn,0,navLp);
        styleEditorToolbarExtras();
    }

    private void styleEditorToolbarExtras(){
        if(quickNavBtn!=null){quickNavBtn.setTextColor(text);quickNavBtn.setBackgroundColor(Color.TRANSPARENT);}
        if(extraToolsBtn!=null){extraToolsBtn.setTextColor(text);extraToolsBtn.setBackgroundColor(Color.TRANSPARENT);}
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
        sheet("أدوات Markdown إضافية",
                new Action("☑ قائمة مهام",this::insertTaskList,false),
                new Action("▦ جدول",()->insertMarkdownBlock("| العمود 1 | العمود 2 |"+System.lineSeparator()+"| --- | --- |"+System.lineSeparator()+"| قيمة | قيمة |"),false),
                new Action("— فاصل أفقي",()->insertMarkdownBlock("---"),false),
                new Action("إلغاء",null,false));
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

    private void configurePreview(){
        WebView.setWebContentsDebuggingEnabled(false); preview.setBackgroundColor(Color.TRANSPARENT); preview.getSettings().setJavaScriptEnabled(true); preview.getSettings().setDomStorageEnabled(false); preview.getSettings().setAllowFileAccess(true); preview.getSettings().setAllowContentAccess(true); preview.addJavascriptInterface(new Bridge(),"Android");
        preview.setFindListener((active,total,done)->{if(done)searchCount.setText(total<=0?"0/0":(active+1)+"/"+total);});
        preview.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){readerReady=true;render();restoreReadingPosition();}@Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){Uri u=r.getUrl();String s=u.getScheme();if(s!=null&&(s.equals("http")||s.equals("https")||s.equals("mailto")||s.equals("tel"))){external(u.toString());return true;}return false;}});
        preview.loadUrl("file:///android_asset/reader.html");
    }

    private void applyTheme(){
        if(dark){bg=Color.rgb(13,17,23);surface=Color.rgb(22,27,34);text=Color.rgb(230,237,243);muted=Color.rgb(139,148,158);border=Color.rgb(48,54,61);accent=Color.rgb(88,166,255);}else{bg=Color.WHITE;surface=Color.rgb(246,248,250);text=Color.rgb(31,35,40);muted=Color.rgb(101,109,118);border=Color.rgb(208,215,222);accent=Color.rgb(37,99,235);}
        root.setBackgroundColor(bg);top.setBackgroundColor(surface);searchBar.setBackgroundColor(surface);formatBar.setBackgroundColor(surface);formatInner.setBackgroundColor(surface);bottom.setBackgroundColor(surface);home.setBackgroundColor(bg);homeContent.setBackgroundColor(bg);editor.setBackgroundColor(bg);editor.setTextColor(text);editor.setHintTextColor(muted);title.setTextColor(text);searchInput.setTextColor(text);searchInput.setHintTextColor(muted);searchInput.setBackground(round(bg,border,9));searchCount.setTextColor(muted);
        tint(top);tint(searchBar);tint(formatInner);for(int i=0;i<formatInner.getChildCount();i++)if(Boolean.TRUE.equals(formatInner.getChildAt(i).getTag()))formatInner.getChildAt(i).setBackgroundColor(border);styleTabs();
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);if(Build.VERSION.SDK_INT>=23){int f=getWindow().getDecorView().getSystemUiVisibility();f=dark?f&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:f|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;getWindow().getDecorView().setSystemUiVisibility(f);}render();if(homeMode)refreshHome();
    }

    private void showHome(){if(!homeMode)saveReadingPosition();homeMode=true;closeSearch();home.setVisibility(View.VISIBLE);preview.setVisibility(View.GONE);editor.setVisibility(View.GONE);formatBar.setVisibility(View.GONE);bottom.setVisibility(View.GONE);homeBtn.setVisibility(View.GONE);openBtn.setVisibility(View.GONE);saveBtn.setVisibility(View.GONE);findBtn.setVisibility(View.GONE);title.setText("MD Reader");refreshHome();hideKeyboard();}
    private void showDocument(){homeMode=false;home.setVisibility(View.GONE);bottom.setVisibility(View.VISIBLE);homeBtn.setVisibility(View.VISIBLE);openBtn.setVisibility(View.VISIBLE);saveBtn.setVisibility(View.VISIBLE);findBtn.setVisibility(View.VISIBLE);if(editing){editor.setVisibility(View.VISIBLE);preview.setVisibility(View.GONE);formatBar.setVisibility(View.VISIBLE);}else{editor.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);formatBar.setVisibility(View.GONE);render();}updateTitle();styleTabs();}

    private void refreshHome(){
        homeContent.removeAllViews(); TextView h=label("MD Reader",30,text,true);homeContent.addView(h);TextView sub=label("قارئ ومحرر Markdown ثنائي الاتجاه",15,muted,false);sub.setPadding(0,dp(4),0,dp(18));homeContent.addView(sub);
        LinearLayout row=new LinearLayout(this);TextView o=homeButton("فتح ملف",true),n=homeButton("ملف جديد",false);o.setOnClickListener(v->openPicker());n.setOnClickListener(v->newFile());row.addView(o,weight(1,0,4));row.addView(n,weight(1,4,0));homeContent.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));
        DraftStore.Draft d=drafts.read();if(d!=null){section("مسودة قابلة للاستعادة");LinearLayout c=card();c.addView(label(d.name,16,text,true));c.addView(label("تغييرات غير محفوظة • "+time(d.time),13,muted,false));LinearLayout r=new LinearLayout(this);TextView restore=mini("استعادة",true),drop=mini("حذف المسودة",false);restore.setOnClickListener(v->restore(d));drop.setOnClickListener(v->choice("حذف المسودة؟","لن تتمكن من استعادتها بعد الحذف.",new Action("حذف",()->{drafts.clear();refreshHome();},true),new Action("إلغاء",null,false)));r.addView(restore,weight(1,0,4));r.addView(drop,weight(1,4,0));c.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));homeContent.addView(c,cardLp());}
        List<RecentStore.Entry> fav=recents.favorites();if(!fav.isEmpty()){section("المفضلة");for(RecentStore.Entry e:fav)docCard(e);}section("المستندات الأخيرة");List<RecentStore.Entry> all=recents.all();if(all.isEmpty()){TextView e=label("لا توجد مستندات بعد\nافتح ملف Markdown وسيظهر هنا تلقائيًا.",15,muted,false);e.setGravity(Gravity.CENTER);e.setPadding(dp(16),dp(28),dp(16),dp(28));e.setBackground(round(surface,border,14));homeContent.addView(e,cardLp());}else for(RecentStore.Entry e:all)docCard(e);
    }

    private void section(String s){TextView v=label(s,18,text,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(24);lp.bottomMargin=dp(10);homeContent.addView(v,lp);}
    private void docCard(RecentStore.Entry e){LinearLayout c=card();c.setClickable(true);c.setOnClickListener(v->load(e.asUri(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));c.setOnLongClickListener(v->{recentActions(e);return true;});LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(label(e.name,16,text,true));b.addView(label(time(e.openedAt),13,muted,false));r.addView(b,new LinearLayout.LayoutParams(0,-2,1));TextView star=label(e.favorite?"★":"☆",24,e.favorite?accent:muted,false);star.setGravity(Gravity.CENTER);star.setOnClickListener(v->{recents.setFavorite(e.uri,!e.favorite);refreshHome();});r.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));c.addView(r);homeContent.addView(c,cardLp());}
    private void recentActions(RecentStore.Entry e){sheet(e.name,new Action(e.favorite?"إزالة من المفضلة":"إضافة إلى المفضلة",()->{recents.setFavorite(e.uri,!e.favorite);refreshHome();},false),new Action("إزالة من السجل",()->{recents.remove(e.uri);refreshHome();},true),new Action("إلغاء",null,false));}
    private void restore(DraftStore.Draft d){currentName=d.name;currentUri=null;if(d.uri!=null&&!d.uri.isEmpty())try{currentUri=Uri.parse(d.uri);}catch(Exception ignored){}setText(d.text,false);dirty=true;showDocument();setEditing(true);}

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

    private void setEditing(boolean on){if(homeMode)showDocument();if(on==editing)return;if(on&&!editing)saveReadingPosition();if(!on&&editing)saveReadingPosition();editing=on;if(on){preview.setVisibility(View.GONE);editor.setVisibility(View.VISIBLE);formatBar.setVisibility(View.VISIBLE);restoreEditorPosition();}else{render();editor.setVisibility(View.GONE);formatBar.setVisibility(View.GONE);preview.setVisibility(View.VISIBLE);hideKeyboard();preview.postDelayed(this::restoreReadingPosition,220);}clearSearch();styleTabs();}
    private void styleTabs(){if(previewTab==null)return;styleTab(previewTab,!editing);styleTab(editTab,editing);}
    private void styleTab(TextView v,boolean selected){v.setTextColor(selected?Color.WHITE:text);v.setBackground(selected?round(accent,accent,10):round(Color.TRANSPARENT,border,10));}
    private void render(){if(!readerReady||preview==null)return;String enc=Base64.encodeToString(txt().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);preview.evaluateJavascript("window.renderMarkdown('"+enc+"','"+(dark?"dark":"light")+"',"+fontSize+");",null);}

    private void more(){List<Action>a=new ArrayList<>();a.add(new Action("ملف جديد",this::newFile,false));if(!homeMode){a.add(new Action("حفظ باسم",this::saveAs,false));a.add(new Action("الفهرس",this::outline,false));a.add(new Action("التنقل السريع",this::documentNavigation,false));a.add(new Action("بحث واستبدال",this::searchReplaceDialog,false));a.add(new Action("سرعة تمرير التحرير",this::editorScrollSettings,false));a.add(new Action("القراءة بالصوت",this::speechMenu,false));a.add(new Action("إضافة علامة مرجعية",this::addBookmark,false));int bc=currentDocKey().isEmpty()?0:reading.bookmarks(currentDocKey()).size();a.add(new Action("العلامات المرجعية"+(bc>0?" ("+bc+")":""),this::bookmarks,false));a.add(new Action("الترجمة",this::translationMenu,false));a.add(new Action("حجم خط القراءة",this::fontDialog,false));a.add(new Action("تصدير PDF",this::pdf,false));a.add(new Action("مشاركة الملف",this::share,false));}a.add(new Action(dark?"الوضع الفاتح":"الوضع الداكن",this::toggleTheme,false));a.add(new Action("حول التطبيق",this::about,false));a.add(new Action("إلغاء",null,false));sheet(homeMode?"MD Reader":currentName,a.toArray(new Action[0]));}
    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean(PREF_DARK,dark).apply();applyTheme();styleEditorToolbarExtras();}
    private void fontDialog(){Dialog d=dialog();LinearLayout p=panel("حجم خط القراءة");TextView val=label(fontSize+" px",19,text,false);val.setGravity(Gravity.CENTER);SeekBar s=new SeekBar(this);s.setMax(15);s.setProgress(fontSize-13);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int x,boolean f){fontSize=13+x;val.setText(fontSize+" px");render();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});p.addView(val,new LinearLayout.LayoutParams(-1,dp(44)));p.addView(s,new LinearLayout.LayoutParams(-1,dp(54)));TextView done=sheetButton("تم",false);done.setOnClickListener(v->{prefs.edit().putInt(PREF_FONT,fontSize).apply();dismissSheet(d,p,null);});p.addView(done,buttonLp());showDialog(d,p,false);}
    private void outline(){List<OutlineParser.Heading> hs=OutlineParser.parse(txt());if(hs.isEmpty()){Toast.makeText(this,"لا توجد عناوين في الملف",Toast.LENGTH_SHORT).show();return;}Dialog d=dialog();LinearLayout p=panel("فهرس المستند");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);for(int i=0;i<hs.size();i++){int idx=i;OutlineParser.Heading h=hs.get(i);TextView r=sheetButton(repeat("   ",Math.max(0,h.level-1))+h.title,false);r.setOnClickListener(v->dismissSheet(d,p,()->{if(editing){jumpEditorOffset(Math.min(h.offset,editor.length()));}else preview.evaluateJavascript("window.scrollToHeading("+idx+");",null);}));list.addView(r,buttonLp());}p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());showDialog(d,p,true);}
    private void pdf(){if(homeMode)return;render();if(editing)setEditing(false);preview.postDelayed(()->{try{PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);PrintDocumentAdapter a=preview.createPrintDocumentAdapter(stripMd(currentName));pm.print(stripMd(currentName),a,null);}catch(Exception e){error("تعذر بدء التصدير",e);}},650);}
    private void share(){if(homeMode)return;if(currentUri==null||dirty){save(this::share);return;}Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/markdown");i.putExtra(Intent.EXTRA_STREAM,currentUri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(i,"مشاركة الملف"));}catch(Exception e){error("تعذر مشاركة الملف",e);}}
    private void about(){message("MD Reader 0.8.0","قارئ ومحرر Markdown يدعم العربية RTL والإنجليزية LTR، الملفات الأخيرة والمفضلة، استعادة المسودات، Mermaid، الترجمة، القراءة بالصوت، حفظ موضع القراءة والتحرير، التحكم بسرعة التمرير، البحث والاستبدال، والتنقل السريع والعلامات المرجعية.\n\nلا إعلانات • لا تحليلات • لا تتبع\n\nمفتاح API الشخصي يُحفظ مشفرًا على الجهاز.");}

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
        LinearLayout controls=new LinearLayout(this);TextView prev=mini("السابق",false);speechPlay=mini("⏸ إيقاف مؤقت",true);TextView next=mini("التالي",false);prev.setOnClickListener(v->speech.previous());speechPlay.setOnClickListener(v->{if(speech.isPaused())speech.resume();else speech.pause();});next.setOnClickListener(v->speech.next());controls.addView(prev,weight(1,0,4));controls.addView(speechPlay,weight(1,4,4));controls.addView(next,weight(1,4,0));p.addView(controls,new LinearLayout.LayoutParams(-1,dp(50)));
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
    private void bookmarks(){String doc=currentDocKey();if(doc.isEmpty()){message("لا يوجد ملف محفوظ","احفظ الملف أولًا لاستخدام العلامات المرجعية.");return;}List<ReadingStore.Bookmark> all=reading.bookmarks(doc);if(all.isEmpty()){message("العلامات المرجعية","لا توجد علامات في هذا المستند بعد.");return;}Dialog d=dialog();LinearLayout p=panel("العلامات المرجعية");ScrollView sc=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);for(ReadingStore.Bookmark b:all){LinearLayout row=new LinearLayout(this);TextView go=sheetButton(b.label+"  •  "+Math.round(b.ratio*100f)+"%",false);go.setOnClickListener(v->dismissSheet(d,p,()->jumpBookmark(b.ratio)));TextView del=mini("حذف",false);del.setOnClickListener(v->{reading.removeBookmark(doc,b.id);dismissSheet(d,p,this::bookmarks);});row.addView(go,new LinearLayout.LayoutParams(0,dp(50),1));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(72),dp(50));dl.setMarginStart(dp(6));row.addView(del,dl);LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,dp(50));rl.bottomMargin=dp(6);list.addView(row,rl);}p.addView(sc,new LinearLayout.LayoutParams(-1,0,1));TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());showDialog(d,p,true);}
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

    private EditText inputField(String hint,String value,boolean secret){EditText e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setSingleLine(true);e.setTextSize(14);e.setTextColor(text);e.setHintTextColor(muted);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(bg,border,10));if(secret)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);return e;}

    private void translate(String source,boolean toAr,TDone done){if(source==null||source.trim().isEmpty())return;String engine=prefs.getString(PREF_TRANSLATION_ENGINE,"ai");if("local".equals(engine))translateLocal(source,toAr,done);else translateAi(source,toAr,done);}
    private void translateAi(String source,boolean toAr,TDone done){String provider=aiTranslator.currentProvider();if(!secrets.hasKey(provider)){choice("مفتاح API غير موجود","أضف مفتاح "+AiTranslationService.providerLabel(provider)+" أولًا أو استخدم الترجمة المحلية.",new Action("إعدادات الترجمة",this::translationSettings,false),new Action("استخدام المحلي الآن",()->translateLocal(source,toAr,done),false),new Action("إلغاء",null,false));return;}Dialog d=dialog();LinearLayout p=panel("جاري الترجمة بالذكاء الاصطناعي");TextView status=label("الاتصال بخدمة AI…",14,muted,false);p.addView(status,textLp());TextView hide=sheetButton("إخفاء",false);hide.setOnClickListener(v->dismissSheet(d,p,null));p.addView(hide,buttonLp());showDialog(d,p,false);aiTranslator.translateMarkdown(source,toAr,new TranslationService.Callback(){public void onStatus(String st){runOnUiThread(()->status.setText(st));}public void onProgress(int c,int total){runOnUiThread(()->status.setText("ترجمة "+c+" من "+total+"…"));}public void onSuccess(String t){runOnUiThread(()->{if(d.isShowing())dismissSheet(d,p,()->done.done(t));else done.done(t);});}public void onError(Exception e){runOnUiThread(()->{Runnable show=()->choice("تعذر إكمال ترجمة AI",e.getMessage()==null?"حدث خطأ أثناء الاتصال":e.getMessage(),new Action("المحاولة محليًا",()->translateLocal(source,toAr,done),false),new Action("إعدادات الترجمة",MainActivity.this::translationSettings,false),new Action("إغلاق",null,false));if(d.isShowing())dismissSheet(d,p,show);else show.run();});}});}
    private void translateLocal(String source,boolean toAr,TDone done){Dialog d=dialog();LinearLayout p=panel("الترجمة المحلية");TextView status=label("جاري التحقق من نموذج اللغة…",14,muted,false);p.addView(status,textLp());p.addView(label("لن يبقى التطبيق في حالة انتظار مفتوحة؛ إذا تعذر تجهيز النموذج ستظهر رسالة خطأ واضحة.",12,muted,false),textLp());TextView hide=sheetButton("إخفاء",false);hide.setOnClickListener(v->dismissSheet(d,p,null));p.addView(hide,buttonLp());showDialog(d,p,false);translator.translateMarkdown(source,toAr,new TranslationService.Callback(){public void onStatus(String st){runOnUiThread(()->status.setText(st));}public void onProgress(int c,int total){runOnUiThread(()->status.setText("ترجمة "+c+" من "+total+"…"));}public void onSuccess(String t){runOnUiThread(()->{Runnable doneRun=()->{if(t.equals(source))Toast.makeText(MainActivity.this,"لا يوجد نص مناسب للترجمة",Toast.LENGTH_SHORT).show();else done.done(t);};if(d.isShowing())dismissSheet(d,p,doneRun);else doneRun.run();});}public void onError(Exception e){runOnUiThread(()->{Runnable show=()->choice("تعذر إكمال الترجمة المحلية",e.getMessage()==null?"فشلت الترجمة المحلية":e.getMessage(),new Action("استخدام AI",()->translateAi(source,toAr,done),false),new Action("إعادة المحاولة",()->translateLocal(source,toAr,done),false),new Action("إغلاق",null,false));if(d.isShowing())dismissSheet(d,p,show);else show.run();});}});}

    private void toggleSearch(){if(homeMode)return;if(searchBar.getVisibility()==View.VISIBLE)closeSearch();else{searchBar.setVisibility(View.VISIBLE);searchInput.requestFocus();keyboard(searchInput);runSearch(searchInput.getText().toString());}}
    private void closeSearch(){if(searchBar==null)return;searchBar.setVisibility(View.GONE);clearSearch();hideKeyboard();}
    private void runSearch(String q){if(q==null||q.isEmpty()){clearSearch();return;}if(editing)editorSearch(q);else preview.findAllAsync(q);}
    private void find(boolean forward){String q=searchInput.getText().toString();if(q.isEmpty())return;if(!editing){preview.findNext(forward);return;}selectSearchMatch(q,false,forward);}
    private void selectSearchMatch(String q,boolean matchCase,boolean forward){
        if(q==null||q.isEmpty())return;
        String source=txt();
        int idx=forward?SearchReplaceEngine.findNext(source,q,Math.max(0,editor.getSelectionEnd()),matchCase):SearchReplaceEngine.findPrevious(source,q,Math.max(0,editor.getSelectionStart()-1),matchCase);
        if(idx<0)idx=forward?SearchReplaceEngine.findNext(source,q,0,matchCase):SearchReplaceEngine.findPrevious(source,q,source.length(),matchCase);
        if(idx>=0){editor.requestFocus();editor.setSelection(idx,idx+q.length());editor.bringPointIntoView(idx);}
        editorSearch(q);
    }
    private void editorSearch(String q){if(q==null||q.isEmpty()){searchCount.setText("0/0");return;}int count=SearchReplaceEngine.count(txt(),q,false);int current=SearchReplaceEngine.ordinalAt(txt(),q,editor.getSelectionStart(),false);searchCount.setText(count==0?"0/0":Math.max(1,current)+"/"+count);}
    private void clearSearch(){if(searchCount!=null)searchCount.setText("0/0");if(preview!=null)preview.clearMatches();}

    private void editorScrollSettings(){
        Dialog d=dialog();LinearLayout p=panel("سرعة تمرير التحرير");
        TextView info=label("100% = سرعة Android الافتراضية. خفّض النسبة لتمرير أهدأ أو ارفعها لانتقال أسرع عند السحب والإفلات.",13,muted,false);p.addView(info,textLp());
        TextView value=label(editorScrollPercent+"%",24,text,true);value.setGravity(Gravity.CENTER);p.addView(value,new LinearLayout.LayoutParams(-1,dp(48)));
        SeekBar speed=new SeekBar(this);speed.setMax(EDITOR_SCROLL_MAX-EDITOR_SCROLL_MIN);speed.setProgress(editorScrollPercent-EDITOR_SCROLL_MIN);
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int x,boolean fromUser){editorScrollPercent=EDITOR_SCROLL_MIN+x;value.setText(editorScrollPercent+"%");if(editorScroller!=null)editorScroller.setSpeedPercent(editorScrollPercent);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();}});
        p.addView(speed,new LinearLayout.LayoutParams(-1,dp(56)));
        LinearLayout row=new LinearLayout(this);TextView reset=mini("إعادة إلى 100%",false),done=mini("تم",true);reset.setOnClickListener(v->{editorScrollPercent=EDITOR_SCROLL_DEFAULT;speed.setProgress(EDITOR_SCROLL_DEFAULT-EDITOR_SCROLL_MIN);prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();});done.setOnClickListener(v->{prefs.edit().putInt(PREF_EDITOR_SCROLL,editorScrollPercent).apply();dismissSheet(d,p,null);});row.addView(reset,weight(1,0,4));row.addView(done,weight(1,4,0));p.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));
        showDialog(d,p,false);
    }

    private EditText multiLineField(String hint,String value,int minLines){EditText e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setSingleLine(false);e.setMinLines(Math.max(2,minLines));e.setMaxLines(6);e.setGravity(Gravity.TOP|Gravity.START);e.setTextSize(15);e.setTextColor(text);e.setHintTextColor(muted);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(round(bg,border,10));e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);return e;}

    private void searchReplaceDialog(){
        if(homeMode)return;
        if(!editing)setEditing(true);
        Dialog d=dialog();LinearLayout p=panel("بحث واستبدال");
        p.addView(label("يقبل نصوصًا كاملة متعددة الأسطر، العربية والإنجليزية والرموز والعلامات بدون تقييد بنوع كلمة.",12,muted,false),textLp());
        EditText query=multiLineField("ابحث عن…",searchInput.getText().toString(),2);
        EditText replacement=multiLineField("استبدل بـ… (يمكن تركه فارغًا للحذف)","",2);
        p.addView(query,textLp());p.addView(replacement,textLp());
        final boolean[] matchCase={false};
        TextView mode=mini("Aa  غير حساس لحالة الأحرف",false);p.addView(mode,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView status=label("",13,muted,false);status.setGravity(Gravity.CENTER);p.addView(status,textLp());
        Runnable refresh=()->{String q=query.getText().toString();int c=SearchReplaceEngine.count(txt(),q,matchCase[0]);int ord=SearchReplaceEngine.ordinalAt(txt(),q,editor.getSelectionStart(),matchCase[0]);status.setText(q.isEmpty()?"أدخل نص البحث":(c==0?"لا توجد نتائج":("النتيجة "+Math.max(1,ord)+" من "+c)));};
        mode.setOnClickListener(v->{matchCase[0]=!matchCase[0];mode.setText(matchCase[0]?"Aa  مطابقة حالة الأحرف":"Aa  غير حساس لحالة الأحرف");refresh.run();});
        query.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable e){refresh.run();}});
        LinearLayout nav=new LinearLayout(this);TextView prev=mini("السابق",false),next=mini("التالي",false);prev.setOnClickListener(v->{selectSearchMatch(query.getText().toString(),matchCase[0],false);refresh.run();});next.setOnClickListener(v->{selectSearchMatch(query.getText().toString(),matchCase[0],true);refresh.run();});nav.addView(prev,weight(1,0,4));nav.addView(next,weight(1,4,0));p.addView(nav,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView replaceOne=sheetButton("استبدال النتيجة الحالية",false);
        replaceOne.setOnClickListener(v->{int n=replaceCurrentSearch(query.getText().toString(),replacement.getText().toString(),matchCase[0]);if(n>0){searchInput.setText(query.getText().toString());refresh.run();}});p.addView(replaceOne,buttonLp());
        TextView replaceAll=sheetButton("استبدال الكل",false);
        replaceAll.setOnClickListener(v->{int n=replaceAllSearch(query.getText().toString(),replacement.getText().toString(),matchCase[0]);if(n>0){searchInput.setText(query.getText().toString());status.setText("تم استبدال "+n+" نتيجة");}});p.addView(replaceAll,buttonLp());
        TextView close=sheetButton("إغلاق",false);close.setOnClickListener(v->dismissSheet(d,p,null));p.addView(close,buttonLp());
        refresh.run();showDialog(d,p,true);query.requestFocus();keyboard(query);
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
    @Override public void onBackPressed(){if(searchBar.getVisibility()==View.VISIBLE){closeSearch();return;}if(!homeMode){confirm(this::showHome);return;}super.onBackPressed();}
    private void updateTitle(){if(title==null)return;title.setText(homeMode?"MD Reader":(dirty?"• ":"")+currentName);}
    private void error(String t,Exception e){String m=e==null?"حدث خطأ غير معروف":e.getMessage();if(m==null||m.isEmpty())m=e==null?"Error":e.getClass().getSimpleName();message(t,m);}

    private static final class Action{final String text;final Runnable run;final boolean danger;Action(String t,Runnable r,boolean d){text=t;run=r;danger=d;}}
    private void sheet(String t,Action...aa){Dialog d=dialog();LinearLayout p=panel(t);for(Action a:aa){TextView b=sheetButton(a.text,a.danger);b.setOnClickListener(v->dismissSheet(d,p,a.run));p.addView(b,buttonLp());}showDialog(d,p,false);}
    private void choice(String t,String m,Action...aa){Dialog d=dialog();LinearLayout p=panel(t);p.addView(label(m,15,muted,false),textLp());for(Action a:aa){TextView b=sheetButton(a.text,a.danger);b.setOnClickListener(v->dismissSheet(d,p,a.run));p.addView(b,buttonLp());}showDialog(d,p,false);}
    private void message(String t,String m){choice(t,m,new Action("حسنًا",null,false));}
    private Dialog dialog(){Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);return d;}
    private LinearLayout panel(String t){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(16),dp(10),dp(16),dp(14));p.setBackground(round(surface,border,22));View handle=new View(this);handle.setBackground(round(border,border,4));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(42),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(14);p.addView(handle,hp);TextView h=label(t,19,text,true);h.setPadding(dp(2),0,dp(2),dp(6));p.addView(h);return p;}
    private void showDialog(Dialog d,View p,boolean tall){
        FrameLayout host=new FrameLayout(this);
        host.setBackgroundColor(Color.TRANSPARENT);
        host.setPadding(dp(10),0,dp(10),dp(10));
        int panelHeight=tall?(int)(getResources().getDisplayMetrics().heightPixels*.86f):ViewGroup.LayoutParams.WRAP_CONTENT;
        FrameLayout.LayoutParams panelLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,panelHeight,Gravity.BOTTOM);
        p.setAlpha(0f);
        p.setVisibility(View.INVISIBLE);
        p.setTranslationY(0f);
        p.setClickable(true);
        host.addView(p,panelLp);
        host.setOnClickListener(v->dismissSheet(d,p,null));
        d.setContentView(host);
        d.setCanceledOnTouchOutside(false);
        Window w=d.getWindow();
        if(w!=null){
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.62f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setWindowAnimations(0);
        }
        d.setOnShowListener(x->{
            Window shown=d.getWindow();
            if(shown!=null)shown.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
            p.post(()->{
                float from=p.getHeight()+dp(18);
                p.setTranslationY(from);
                p.setAlpha(1f);
                p.setVisibility(View.VISIBLE);
                p.animate().cancel();
                p.postOnAnimation(()->p.animate().translationY(0f).alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start());
            });
        });
        d.show();
    }
    private void dismissSheet(Dialog d,View p,Runnable after){
        if(d==null||!d.isShowing()){if(after!=null)after.run();return;}
        p.animate().cancel();
        p.animate().translationY(p.getHeight()+dp(18)).alpha(0f).setDuration(150).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{try{d.dismiss();}catch(Exception ignored){}if(after!=null)after.run();}).start();
    }
    private TextView sheetButton(String s,boolean danger){TextView v=label(s,16,danger?Color.rgb(248,113,113):text,false);v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);v.setPadding(dp(16),0,dp(16),0);v.setBackground(round(bg,border,13));return v;}

    private TextView topAction(String s,int size){TextView v=label(s,size,text,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setMinWidth(dp(48));v.setClickable(true);return v;}
    private TextView small(String s){TextView v=topAction(s,14);v.setLayoutParams(new LinearLayout.LayoutParams(-2,-1));return v;}
    private TextView tab(String s){TextView v=label(s,15,text,true);v.setGravity(Gravity.CENTER);return v;}
    private void format(String s,View.OnClickListener l){TextView v=label(s,14,text,false);v.setGravity(Gravity.CENTER);v.setPadding(dp(11),0,dp(11),0);v.setMinWidth(dp(42));v.setOnClickListener(l);formatInner.addView(v,new LinearLayout.LayoutParams(-2,-1));}
    private void divider(){View v=new View(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(1),dp(26));lp.setMargins(dp(4),0,dp(4),0);v.setTag(Boolean.TRUE);formatInner.addView(v,lp);}
    private TextView label(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setGravity(Gravity.START);return v;}
    private TextView homeButton(String s,boolean primary){TextView v=label(s,16,primary?Color.WHITE:text,true);v.setGravity(Gravity.CENTER);v.setBackground(primary?round(accent,accent,12):round(surface,border,12));return v;}
    private TextView mini(String s,boolean primary){TextView v=homeButton(s,primary);v.setTextSize(14);return v;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(12),dp(14));c.setBackground(round(surface,border,14));return c;}
    private LinearLayout.LayoutParams cardLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);return p;}private LinearLayout.LayoutParams buttonLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.topMargin=dp(6);return p;}private LinearLayout.LayoutParams textLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);p.bottomMargin=dp(8);return p;}private LinearLayout.LayoutParams weight(float w,int st,int en){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,w);p.setMarginStart(dp(st));p.setMarginEnd(dp(en));return p;}
    private GradientDrawable round(int fill,int stroke,int r){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));d.setStroke(dp(1),stroke);return d;}
    private void tint(ViewGroup g){for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i) instanceof TextView&&g.getChildAt(i)!=title&&g.getChildAt(i)!=searchInput)((TextView)g.getChildAt(i)).setTextColor(text);}
    private void keyboard(View v){v.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT),100);}private void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
    private void external(String s){try{Uri u=Uri.parse(s);String sc=u.getScheme();if(sc!=null&&(sc.equals("http")||sc.equals("https")||sc.equals("mailto")||sc.equals("tel")))startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}}
    private String txt(){return editor==null?"":editor.getText().toString();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}private static String ensureMd(String n){if(n==null||n.isEmpty())n="document.md";String l=n.toLowerCase(Locale.ROOT);return l.endsWith(".md")||l.endsWith(".markdown")?n:n+".md";}private static String stripMd(String n){return n==null?"Markdown":n.replaceFirst("(?i)\\.(md|markdown)$","");}private static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}private String time(long t){return t<=0?"":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t));}

    @Override protected void onDestroy(){if(draftPending!=null)main.removeCallbacks(draftPending);io.shutdownNow();if(speech!=null)speech.shutdown();aiTranslator.shutdown();history.cancel();if(preview!=null){preview.removeJavascriptInterface("Android");preview.destroy();}super.onDestroy();}
    private class Bridge{@JavascriptInterface public void copyText(String s){runOnUiThread(()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("code",s==null?"":s));Toast.makeText(MainActivity.this,"تم النسخ",Toast.LENGTH_SHORT).show();});}@JavascriptInterface public void openLink(String s){runOnUiThread(()->external(s));}}
    private abstract static class Watcher implements TextWatcher{public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){}}
    private class History{final List<String> undo=new ArrayList<>(),redo=new ArrayList<>();String cur="";Runnable pending;void reset(String s){cancel();undo.clear();redo.clear();cur=s==null?"":s;}void schedule(String s){cancel();pending=()->checkpoint(s);main.postDelayed(pending,450);}void checkpoint(String s){cancel();if(s==null)s="";if(s.equals(cur))return;undo.add(cur);if(undo.size()>80)undo.remove(0);cur=s;redo.clear();}String undo(String actual){cancel();if(!actual.equals(cur))checkpoint(actual);if(undo.isEmpty())return null;redo.add(cur);cur=undo.remove(undo.size()-1);return cur;}String redo(String actual){cancel();if(!actual.equals(cur))checkpoint(actual);if(redo.isEmpty())return null;undo.add(cur);cur=redo.remove(redo.size()-1);return cur;}void cancel(){if(pending!=null){main.removeCallbacks(pending);pending=null;}}}
}
