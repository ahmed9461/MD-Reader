package app.mdreader.mobile;

public final class SafeHtmlEditorToolsSmokeTest {
    public static void main(String[] args){
        eq("<mark>hello</mark>",SafeHtmlEditorTools.highlight("hello",0,5).text,"mark");
        eq("<u>hello</u>",SafeHtmlEditorTools.underline("hello",0,5).text,"underline");
        eq("<span style=\"color:#0969DA\">hello</span>",SafeHtmlEditorTools.textColor("hello",0,5,"#0969da").text,"color");
        eq("<span style=\"background-color:#FFF8C5\">hello</span>",SafeHtmlEditorTools.backgroundColor("hello",0,5,"#fff8c5").text,"background");
        eq("<span style=\"display:block;text-align:center\">hello</span>",SafeHtmlEditorTools.alignment("hello",0,5,"center").text,"alignment");
        eq("<span dir=\"rtl\">hello</span>",SafeHtmlEditorTools.direction("hello",0,5,"rtl").text,"direction");
        if(!SafeHtmlEditorTools.isSafeColor("#ABC"))fail("short hex should be valid");
        if(SafeHtmlEditorTools.isSafeColor("url(javascript:1)"))fail("unsafe color accepted");
        boolean threw=false;try{SafeHtmlEditorTools.textColor("x",0,1,"expression(alert(1))");}catch(IllegalArgumentException expected){threw=true;}
        if(!threw)fail("invalid color must fail closed");
        if(!SafeHtmlEditorTools.details("body",0,4).text.contains("<summary>التفاصيل</summary>"))fail("details template");
        eq("<br>",SafeHtmlEditorTools.lineBreak("",0,0).text,"br");
        System.out.println("SafeHtmlEditorToolsSmokeTest OK");
    }
    private static void eq(String expected,String actual,String label){if(!expected.equals(actual))fail(label+" expected="+expected+" actual="+actual);}
    private static void fail(String message){throw new AssertionError(message);}
}
