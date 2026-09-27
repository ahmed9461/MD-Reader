package app.mdreader.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One scrollable body below an always-visible heading/close action. Uses available window space. */
final class ResponsiveSheet {
    private ResponsiveSheet(){}
    static LinearLayout panel(Context c,String title,UiPalette palette){
        LinearLayout p=ReaderUi.column(c);p.setTag(title);p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return p;
    }
    static void show(Dialog dialog,LinearLayout content,UiPalette palette){
        Context c=content.getContext();
        // Older callers supplied a weight=1 ScrollView for lists. Unwrap it before measuring
        // the single shared scroll body, instead of retaining a zero-height nested scroller.
        for(int i=0;i<content.getChildCount();i++){
            View child=content.getChildAt(i);
            if(child instanceof ScrollView){
                ScrollView old=(ScrollView)child;View body=old.getChildCount()>0?old.getChildAt(0):null;
                old.removeAllViews();content.removeViewAt(i);
                if(body!=null)content.addView(body,i,new LinearLayout.LayoutParams(-1,-2));else i--;
            }
        }
        FrameLayout host=new FrameLayout(c);host.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        host.setPadding(ReaderUi.dp(c,12),ReaderUi.dp(c,12),ReaderUi.dp(c,12),ReaderUi.dp(c,12));
        host.setOnClickListener(v->dialog.dismiss());
        LinearLayout shell=new CappedColumn(c);shell.setOrientation(LinearLayout.VERTICAL);shell.setClickable(true);
        shell.setTag("md-sheet-shell");shell.setBackground(ReaderUi.shape(c,palette.surface,palette.border,24));
        shell.setPadding(ReaderUi.dp(c,12),ReaderUi.dp(c,6),ReaderUi.dp(c,12),ReaderUi.dp(c,8));
        LinearLayout header=ReaderUi.row(c);header.setMinimumHeight(ReaderUi.dp(c,56));
        TextView title=ReaderUi.text(c,String.valueOf(content.getTag()==null?"":content.getTag()),18,palette.text,true);
        title.setPadding(ReaderUi.dp(c,8),0,ReaderUi.dp(c,8),0);title.setMaxLines(2);header.addView(title,ReaderUi.weighted());
        TextView close=ReaderUi.icon(c,"إغلاق اللوحة",UiIcon.Kind.CLOSE,palette);ReaderUi.accessibleAction(close);close.setOnClickListener(v->dialog.dismiss());header.addView(close,ReaderUi.touch(c));
        shell.addView(header,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(c);scroll.setClipToPadding(false);scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        content.setPadding(ReaderUi.dp(c,4),0,ReaderUi.dp(c,4),ReaderUi.dp(c,8));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));shell.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);host.addView(shell,lp);
        dialog.setContentView(host);Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount(.42f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setWindowAnimations(0);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
        }
        host.setOnApplyWindowInsetsListener((v,insets)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());l=i.left;t=i.top;r=i.right;b=i.bottom;}
            else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}
            int pad=ReaderUi.dp(c,12);v.setPadding(l+pad,t+pad,r+pad,b+pad);return insets;
        });
        dialog.show();if(window!=null)window.setLayout(-1,-1);host.requestApplyInsets();
    }
    private static final class CappedColumn extends LinearLayout {
        CappedColumn(Context c){super(c);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=Math.min(MeasureSpec.getSize(widthSpec),ReaderUi.dp(getContext(),560));
            int height=Math.min(MeasureSpec.getSize(heightSpec),ReaderUi.dp(getContext(),760));
            super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.AT_MOST));
        }
    }
}
