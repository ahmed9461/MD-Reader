package app.mdreader.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared spacing, states, fields and accessible actions, without a UI framework dependency. */
final class ReaderUi {
    private ReaderUi() {}
    static int dp(Context c, int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c, int fill, int border, int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));
        if(border!=Color.TRANSPARENT)d.setStroke(dp(c,1),border);return d;
    }
    static Drawable ripple(Context c, int fill, int border, int radius, int ink) {
        return new RippleDrawable(ColorStateList.valueOf(UiPalette.alpha(ink,40)),shape(c,fill,border,radius),shape(c,Color.WHITE,Color.TRANSPARENT,radius));
    }
    static TextView text(Context c,String label,int sp,int color,boolean bold) {
        TextView v=new TextView(c);v.setText(label);v.setTextSize(sp);v.setTextColor(color);
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;
    }
    static TextView icon(Context c, String label, UiIcon.Kind kind, UiPalette p) {
        TextView v=text(c,"",14,p.text,false);v.setContentDescription(label);v.setTooltipText(label);
        v.setGravity(Gravity.CENTER);v.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        v.setCompoundDrawablesRelative(new UiIcon(kind,p.text,dp(c,24)),null,null,null);
        v.setMinWidth(dp(c,48));v.setMinHeight(dp(c,48));v.setFocusable(true);
        v.setBackground(ripple(c,Color.TRANSPARENT,Color.TRANSPARENT,12,p.accent));return v;
    }
    static TextView button(Context c,String label,boolean primary,UiPalette p) {
        TextView v=text(c,label,15,primary?p.onAccent:p.text,true);v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c,48));v.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));v.setFocusable(true);
        v.setBackground(ripple(c,primary?p.accent:p.raised,Color.TRANSPARENT,14,p.text));return v;
    }
    static void selected(TextView v, boolean selected, UiPalette p) {
        v.setSelected(selected);v.setTextColor(selected?p.accent:p.muted);
        v.setBackground(ripple(v.getContext(),selected?p.accentSoft:Color.TRANSPARENT,Color.TRANSPARENT,12,p.accent));
        tintIcons(v,selected?p.accent:p.muted);
    }
    static void tintIcons(TextView v,int color) {
        for(Drawable d:v.getCompoundDrawablesRelative())if(d!=null)d.setTint(color);
    }
    static void accessibleAction(TextView v) {
        v.setFocusable(true);v.setMinHeight(dp(v.getContext(),48));
        v.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View view,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(view,info);info.setClassName("android.widget.Button");
            }
        });
    }
    static void field(EditText v,UiPalette p) {
        Context c=v.getContext();v.setTextColor(p.text);v.setHintTextColor(p.muted);v.setTextSize(15);
        v.setMinHeight(dp(c,48));v.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);v.setGravity(Gravity.TOP|Gravity.START);
        v.setImeOptions(v.getImeOptions()|EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        StateListDrawable bg=new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused},shape(c,p.background,p.accent,12));
        bg.addState(new int[]{},shape(c,p.background,p.border,12));v.setBackground(bg);
    }
    static void enable(TextView v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1f:.38f);}
    static LinearLayout row(Context c){LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    static LinearLayout column(Context c){LinearLayout r=new LinearLayout(c);r.setOrientation(LinearLayout.VERTICAL);return r;}
    static LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    static LinearLayout.LayoutParams touch(Context c){return new LinearLayout.LayoutParams(dp(c,48),dp(c,48));}

    static UiIcon.Kind kindFor(String s) {
        if(s.contains("حذف")||s.contains("إزالة")||s.contains("توحيد"))return UiIcon.Kind.ERASE;
        if(s.contains("إلغاء")||s.contains("إغلاق"))return UiIcon.Kind.CLOSE;
        if(s.contains("استبدال"))return UiIcon.Kind.REPLACE;
        if(s.contains("بحث"))return UiIcon.Kind.SEARCH;
        if(s.contains("GitHub")||s.contains("تنزيل"))return UiIcon.Kind.DOWNLOAD;
        if(s.contains("حفظ"))return UiIcon.Kind.SAVE;
        if(s.contains("جديد")||s.contains("إضافة")||s.contains("إدراج"))return UiIcon.Kind.PLUS;
        if(s.contains("مفضلة"))return UiIcon.Kind.STAR;
        if(s.contains("علام")||s.contains("مرجعي"))return UiIcon.Kind.BOOKMARK;
        if(s.contains("صوت")||s.contains("قراءة المحدد"))return UiIcon.Kind.SPEAKER;
        if(s.contains("الفهرس")||s.contains("فهرس"))return UiIcon.Kind.OUTLINE;
        if(s.contains("جدول"))return UiIcon.Kind.TABLE;
        if(s.contains("حاوية")||s.contains("HTML"))return UiIcon.Kind.CODE;
        if(s.contains("خط")||s.contains("ترجم"))return UiIcon.Kind.TEXT;
        if(s.contains("إعدادات")||s.contains("سرعة"))return UiIcon.Kind.SETTINGS;
        if(s.contains("مشاركة"))return UiIcon.Kind.SHARE;
        if(s.contains("PDF"))return UiIcon.Kind.FILE;
        if(s.contains("داكن"))return UiIcon.Kind.MOON;
        if(s.contains("فاتح"))return UiIcon.Kind.SUN;
        if(s.contains("إجابة صحيحة")||s.contains("تم")||s.contains("حسنًا"))return UiIcon.Kind.CHECK;
        if(s.contains("مهام")||s.contains("اختيار"))return UiIcon.Kind.BOX;
        if(s.contains("حول"))return UiIcon.Kind.INFO;
        return UiIcon.Kind.BACK;
    }
    static String cleanLabel(String s){return s.replaceFirst("^[✅☐☑🧹◇▣▦⏸▶]+\\s*","");}
}
