package app.mdreader.mobile;

/** Pure editor transformations for the safe HTML subset supported by MD Reader. */
final class SafeHtmlEditorTools {
    private SafeHtmlEditorTools() { }

    static MarkdownTransforms.Result highlight(String text,int start,int end){
        return wrap(text,start,end,"<mark>","</mark>","نص مميز");
    }
    static MarkdownTransforms.Result underline(String text,int start,int end){
        return wrap(text,start,end,"<u>","</u>","نص مسطر");
    }
    static MarkdownTransforms.Result superscript(String text,int start,int end){
        return wrap(text,start,end,"<sup>","</sup>","2");
    }
    static MarkdownTransforms.Result subscript(String text,int start,int end){
        return wrap(text,start,end,"<sub>","</sub>","2");
    }
    static MarkdownTransforms.Result keyboard(String text,int start,int end){
        return wrap(text,start,end,"<kbd>","</kbd>","Ctrl+C");
    }
    static MarkdownTransforms.Result small(String text,int start,int end){
        return wrap(text,start,end,"<small>","</small>","نص صغير");
    }
    static MarkdownTransforms.Result textColor(String text,int start,int end,String color){
        String safe=requireColor(color);
        return wrap(text,start,end,"<span style=\"color:"+safe+"\">","</span>","نص ملون");
    }
    static MarkdownTransforms.Result backgroundColor(String text,int start,int end,String color){
        String safe=requireColor(color);
        return wrap(text,start,end,"<span style=\"background-color:"+safe+"\">","</span>","نص مظلل");
    }
    static MarkdownTransforms.Result alignment(String text,int start,int end,String alignment){
        String a=alignment==null?"":alignment.trim().toLowerCase();
        if(!a.matches("left|right|center|justify|start|end"))throw new IllegalArgumentException("Unsupported alignment");
        return wrap(text,start,end,"<span style=\"display:block;text-align:"+a+"\">","</span>","نص");
    }
    static MarkdownTransforms.Result direction(String text,int start,int end,String direction){
        String d=direction==null?"":direction.trim().toLowerCase();
        if(!d.matches("rtl|ltr|auto"))throw new IllegalArgumentException("Unsupported direction");
        return wrap(text,start,end,"<span dir=\""+d+"\">","</span>","نص");
    }
    static MarkdownTransforms.Result details(String text,int start,int end){
        return MarkdownTransforms.wrap(text,start,end,"<details>\n<summary>التفاصيل</summary>\n","\n</details>","المحتوى");
    }
    static MarkdownTransforms.Result lineBreak(String text,int start,int end){
        return MarkdownTransforms.insert(text,start,end,"<br>");
    }
    static boolean isSafeColor(String color){return normalizeColor(color)!=null;}

    private static MarkdownTransforms.Result wrap(String text,int start,int end,String before,String after,String placeholder){
        return MarkdownTransforms.wrap(text,start,end,before,after,placeholder);
    }
    private static String requireColor(String color){
        String value=normalizeColor(color);
        if(value==null)throw new IllegalArgumentException("Unsupported color");
        return value;
    }
    private static String normalizeColor(String color){
        if(color==null)return null;
        String v=color.trim();
        if(v.matches("(?i)^(#[0-9a-f]{3}|#[0-9a-f]{4}|#[0-9a-f]{6}|#[0-9a-f]{8})$"))return v.toUpperCase();
        String lower=v.toLowerCase();
        if(lower.matches("black|white|gray|grey|red|green|blue|yellow|orange|purple|pink|brown|cyan|magenta|teal|navy|maroon|olive|lime|aqua|silver|transparent|currentcolor"))return lower;
        return null;
    }
}
