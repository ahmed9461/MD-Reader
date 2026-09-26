package app.mdreader.mobile;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** In-layout search: the document is resized, never covered by a modal window. */
final class FindReplaceBar extends LinearLayout {
    interface Listener {
        void queryChanged(); void navigate(boolean forward); void close(); void hideKeyboard();
        void expandReplacement(); void replaceOne(); void replaceAll();
    }
    final EditText query,replacement;
    final TextView count,expand;
    private final TextView previous,next,close,caseButton,keyboard,replaceOne,replaceAll;
    private final LinearLayout options,replacementRow;
    private final Listener listener;
    private UiPalette palette;
    private boolean matchCase,expanded,editing,compact;
    FindReplaceBar(Context c,UiPalette palette,Listener listener){
        super(c);this.palette=palette;this.listener=listener;
        setOrientation(VERTICAL);setPadding(ReaderUi.dp(c,8),ReaderUi.dp(c,4),ReaderUi.dp(c,8),ReaderUi.dp(c,4));
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);setTag("find-replace-bar");
        LinearLayout search=ReaderUi.row(c);
        query=field(c,"ابحث في المستند…");query.setContentDescription("نص البحث — يدعم عدة أسطر");query.setTag("find-query");
        search.addView(query,ReaderUi.weighted());
        previous=action(c,"النتيجة السابقة",UiIcon.Kind.UP,()->listener.navigate(false));
        next=action(c,"النتيجة التالية",UiIcon.Kind.DOWN,()->listener.navigate(true));
        close=action(c,"إغلاق البحث",UiIcon.Kind.CLOSE,listener::close);
        search.addView(previous,ReaderUi.touch(c));search.addView(next,ReaderUi.touch(c));search.addView(close,ReaderUi.touch(c));
        addView(search,new LayoutParams(-1,-2));
        options=ReaderUi.row(c);
        count=ReaderUi.text(c,"أدخل نص البحث",12,palette.muted,false);count.setPadding(ReaderUi.dp(c,8),0,0,0);
        count.setMaxLines(2);count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        options.addView(count,ReaderUi.weighted());
        caseButton=ReaderUi.button(c,"Aa",false,palette);caseButton.setPadding(0,0,0,0);
        caseButton.setOnClickListener(v->{matchCase=!matchCase;theme(this.palette);listener.queryChanged();});
        options.addView(caseButton,ReaderUi.touch(c));
        expand=ReaderUi.button(c,"استبدال",false,palette);expand.setPadding(ReaderUi.dp(c,10),0,ReaderUi.dp(c,10),0);
        expand.setOnClickListener(v->{if(!expanded)listener.expandReplacement();setExpanded(!expanded);});
        options.addView(expand,new LayoutParams(-2,-2));
        keyboard=action(c,"إخفاء لوحة المفاتيح",UiIcon.Kind.KEYBOARD,listener::hideKeyboard);
        options.addView(keyboard,ReaderUi.touch(c));addView(options,new LayoutParams(-1,-2));
        replacementRow=ReaderUi.row(c);replacement=field(c,"البديل — فارغ للحذف");replacement.setContentDescription("النص البديل — اتركه فارغًا للحذف");
        replacement.setTag("replace-input");replacementRow.addView(replacement,ReaderUi.weighted());
        replaceOne=action(c,"استبدال النتيجة الحالية",UiIcon.Kind.REPLACE,listener::replaceOne);
        replaceAll=action(c,"استبدال جميع النتائج…",UiIcon.Kind.REPLACE_ALL,listener::replaceAll);
        replacementRow.addView(replaceOne,ReaderUi.touch(c));replacementRow.addView(replaceAll,ReaderUi.touch(c));
        addView(replacementRow,new LayoutParams(-1,-2));setExpanded(false);
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable e){listener.queryChanged();}
        });theme(palette);setResults(0,0);
    }
    private EditText field(Context c,String hint){
        EditText e=new EditText(c);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(1);e.setMaxLines(2);e.setHorizontallyScrolling(false);ReaderUi.field(e,palette);return e;
    }
    private TextView action(Context c,String label,UiIcon.Kind kind,Runnable r){
        TextView v=ReaderUi.icon(c,label,kind,palette);ReaderUi.accessibleAction(v);v.setOnClickListener(w->r.run());return v;
    }
    boolean matchesCase(){return editing&&matchCase;}
    boolean isExpanded(){return expanded;}
    void setEditorMode(boolean value){editing=value;if(!value)setExpanded(false);caseButton.setVisibility(value?VISIBLE:GONE);}
    void setExpanded(boolean value){expanded=value;replacementRow.setVisibility(value&&!compact?VISIBLE:GONE);ReaderUi.selected(expand,value,palette);expand.setContentDescription(value?"طي خيارات الاستبدال":"إظهار خيارات الاستبدال");}
    void setCompact(boolean value){
        if(compact==value)return;compact=value;options.setVisibility(value?GONE:VISIBLE);
        replacementRow.setVisibility(expanded&&!value?VISIBLE:GONE);query.setMaxLines(value?1:2);replacement.setMaxLines(value?1:2);
    }
    void setResults(int ordinal,int total){
        count.setText(query.length()==0?"أدخل نص البحث":total==0?"لا توجد نتائج":ordinal+" من "+total);
        count.setContentDescription(total==0?"لا توجد نتائج":"النتيجة "+ordinal+" من "+total);
        for(TextView v:new TextView[]{previous,next,replaceOne,replaceAll})ReaderUi.enable(v,total>0);
    }
    void theme(UiPalette p){
        palette=p;setBackgroundColor(p.surface);ReaderUi.field(query,p);ReaderUi.field(replacement,p);count.setTextColor(p.muted);
        for(TextView v:new TextView[]{previous,next,close,keyboard,replaceOne,replaceAll}){ReaderUi.tintIcons(v,p.text);v.setBackground(ReaderUi.ripple(getContext(),0,0,12,p.accent));}
        ReaderUi.selected(caseButton,matchCase,p);ReaderUi.selected(expand,expanded,p);
        caseButton.setContentDescription(matchCase?"مطابقة حالة الأحرف مفعلة":"مطابقة حالة الأحرف غير مفعلة");
        caseButton.setTooltipText(caseButton.getContentDescription());
    }
}
