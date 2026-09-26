package app.mdreader.mobile;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Dependency-free device regression runner. Never packaged in the release app. */
public final class UiRegressionInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int assertions;
    private String sample;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            getTargetContext().getSharedPreferences("md_reader_preferences",0).edit().clear().commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            pause();
            sample="# دفتر الأفكار\n\nمساحة هادئة للقراءة والكتابة بالعربية وEnglish.\n\n## خطة اليوم\n\n- [x] قراءة الفصل الأول\n- [ ] مراجعة الملاحظات\n\n```python title=\"مثال سريع\"\nprint(\"Hello, Markdown!\")\n```\n\n## ملاحظات\n\n> الأفكار الواضحة تبدأ بصفحة بسيطة.\n";
            ui(()->{
                RecentStore store=(RecentStore)field("recents");
                for(String name:new String[]{"دفتر الأفكار.md","ملاحظات المحاضرة.md","README.md","خطة الأسبوع.md"}){
                    File file=new File(activity.getFilesDir(),name);try(FileOutputStream out=new FileOutputStream(file)){out.write(sample.getBytes(StandardCharsets.UTF_8));}
                    store.touch(Uri.fromFile(file),name);if(name.contains("دفتر")||name.contains("خطة"))store.setFavorite(Uri.fromFile(file).toString(),true);
                }call("refreshHome");
            });pause();shot("01-home-light");
            ui(()->{TextView[] tabs=(TextView[])field("homeTabs");check(tabs.length==4,"four library tabs");tabs[1].performClick();});pause();shot("02-documents-light");
            ui(()->{call("toggleTheme");call("switchHomePage",new Class[]{int.class},2);});pause();shot("03-favorites-dark");
            ui(()->call("switchHomePage",new Class[]{int.class},3));pause();shot("04-settings-dark");
            ui(()->{set("currentName","دفتر الأفكار.md");call("setText",new Class[]{String.class,boolean.class},sample,true);call("showDocument");call("setEditing",new Class[]{boolean.class},false);});
            SystemClock.sleep(1600);waitForIdleSync();shot("05-reader-dark");
            StringBuilder source=new StringBuilder("Q1 البداية\n");for(int i=0;i<80;i++)source.append("سطر ").append(i+1).append(" — نص تجريبي للقراءة والبحث.\n");source.append("Q1 النهاية\nq1 small\nألف\nباء\nألف\nباء");
            final String document=source.toString();
            ui(()->{call("setText",new Class[]{String.class,boolean.class},document,true);call("setEditing",new Class[]{boolean.class},true);call("searchReplaceDialog");((EditText)field("searchInput")).setText("Q1");});
            waitForKeyboard();
            ui(()->{
                View editor=(View)field("editor"),panel=(View)field("searchBar");int[] e=new int[2],p=new int[2];editor.getLocationOnScreen(e);panel.getLocationOnScreen(p);
                check(e[1]>=p[1]+panel.getHeight(),"search does not overlay document");check(editor.getHeight()>=dp(120),"useful editor viewport above keyboard");
                check(((EditText)field("searchInput")).hasFocus(),"query keeps focus");
                check(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()),"keyboard really shown");
            });shot("06-find-keyboard-dark");
            ui(()->{
                FindReplaceBar p=(FindReplaceBar)field("findPanel");check(p.count.getText().toString().contains("3"),"case insensitive result count");
                call("find",new Class[]{boolean.class},false);
                check(((EditText)field("editor")).getSelectionStart()==document.indexOf("q1 small"),"previous wraps to final match");
                check(p.query.hasFocus(),"navigation does not steal query focus");
            });pause();ui(()->assertMatchVisible());shot("07-find-result-dark");
            ui(()->{call("find",new Class[]{boolean.class},true);check(((EditText)field("editor")).getSelectionStart()==0,"next wraps to first");
                ((FindReplaceBar)field("findPanel")).replacement.setText("السؤال");call("replaceInline",new Class[]{boolean.class},false);
                check(((EditText)field("editor")).getText().toString().startsWith("السؤال"),"replace one");call("undo");check(((EditText)field("editor")).getText().toString().equals(document),"undo restores exact document");
                int n=(Integer)call("replaceAllSearch",new Class[]{String.class,String.class,boolean.class},"Q1","",false);check(n==3,"replace all includes case-insensitive matches");call("undo");check(((EditText)field("editor")).getText().toString().equals(document),"replace all undo");
                ((EditText)field("searchInput")).setText("ألف\nباء");
            });pause();ui(()->check(((FindReplaceBar)field("findPanel")).count.getText().toString().contains("2"),"multiline Arabic search"));
            ui(()->((EditText)field("searchInput")).setText("not-present"));pause();ui(()->check(((FindReplaceBar)field("findPanel")).count.getText().toString().equals("لا توجد نتائج"),"zero-result state"));
            ui(()->{call("closeSearch");call("more");});pause();assertCloseVisible();shot("08-document-menu-dark");closeSheet();
            ui(()->call("titledCodeBlockTool"));pause();assertCloseVisible();shot("09-code-title-dialog-dark");closeSheet();
            ui(()->SpeechSettingsDialog.show(activity,(SpeechReader)field("speech")));pause();assertCloseVisible();shot("10-speech-settings-dark");closeSheet();
            ui(()->{call("searchReplaceDialog");((EditText)field("searchInput")).setText("Q1");activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});SystemClock.sleep(1600);waitForIdleSync();
            ui(()->{check(((EditText)field("editor")).getText().toString().equals(document),"rotation preserves document");View e=(View)field("editor");check(e.getHeight()>dp(60),"landscape leaves document viewport");
                FindReplaceBar p=(FindReplaceBar)field("findPanel");int[] ep=new int[2],pp=new int[2];e.getLocationOnScreen(ep);p.getLocationOnScreen(pp);
                check(ep[0]+e.getWidth()<=pp[0]||pp[0]+p.getWidth()<=ep[0],"short landscape search is beside document");
                check(p.replacement.isShown(),"expanded replacement remains available in short landscape");
                check(p.query.getHeight()>=dp(48)&&p.replacement.getHeight()>=dp(48),"landscape fields retain touch targets");
            });shot("11-landscape-find");
            ui(()->{activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);call("closeSearch");});pause();
            ui(()->call("translationSettings"));pause();assertCloseVisible();shot("12-translation-settings-dark");closeSheet();
            result.putString("stream","UI_REGRESSION_PASS: "+assertions+" assertions; screenshots captured.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){android.util.Log.e("MDReaderUiTest","UI regression failure",e);result.putString("stream","UI_REGRESSION_FAIL: "+android.util.Log.getStackTraceString(e));try{shot("failure");}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}
    }
    private void waitForKeyboard() throws Exception {
        long deadline=SystemClock.uptimeMillis()+8000;final boolean[] visible={false};
        do {pause();ui(()->visible[0]=activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.ime()));}
        while(!visible[0]&&SystemClock.uptimeMillis()<deadline);
        check(visible[0],"keyboard shown within cold-start deadline");
    }
    private void assertMatchVisible() throws Exception {
        EditText e=(EditText)field("editor");android.text.Layout layout=e.getLayout();int line=layout.getLineForOffset(e.getSelectionStart());
        int y=layout.getLineTop(line)-e.getScrollY();
        check(y>=0&&y<e.getHeight()-e.getTotalPaddingTop()-e.getTotalPaddingBottom(),"selected match is inside visible document viewport");
    }
    private interface Work{void run() throws Exception;}
    private void ui(Work r) throws Exception {final Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object call(String name)throws Exception{return call(name,new Class[0]);}
    private Object call(String name,Class[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private void pause(){SystemClock.sleep(550);waitForIdleSync();}
    private void shot(String name)throws Exception{pause();Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("No screenshot");File dir=new File(getTargetContext().getExternalFilesDir(null),"ui-screenshots");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private AccessibilityNodeInfo closeNode(AccessibilityNodeInfo node){if(node==null)return null;if("إغلاق اللوحة".contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo n=closeNode(node.getChild(i));if(n!=null)return n;}return null;}
    private void assertCloseVisible(){AccessibilityNodeInfo close=closeNode(getUiAutomation().getRootInActiveWindow());check(close!=null&&close.isVisibleToUser(),"sheet close is always visible");if(close!=null){Rect b=new Rect();close.getBoundsInScreen(b);check(b.width()>=dp(48)&&b.height()>=dp(48),"48dp sheet close target");}}
    private void closeSheet(){AccessibilityNodeInfo close=closeNode(getUiAutomation().getRootInActiveWindow());if(close==null)throw new AssertionError("missing close");close.performAction(AccessibilityNodeInfo.ACTION_CLICK);pause();}
}
