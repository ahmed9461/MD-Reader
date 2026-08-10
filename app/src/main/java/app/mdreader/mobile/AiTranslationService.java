package app.mdreader.mobile;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AiTranslationService {
    static final String PREF_PROVIDER = "translation_ai_provider";
    static final String PROVIDER_OPENAI = ApiKeyStore.PROVIDER_OPENAI;
    static final String PROVIDER_DEEPSEEK = ApiKeyStore.PROVIDER_DEEPSEEK;

    static final String PREF_MODEL = "translation_ai_model";
    static final String PREF_ENDPOINT = "translation_ai_endpoint";

    static final String PREF_OPENAI_MODEL = "translation_openai_model";
    static final String PREF_OPENAI_ENDPOINT = "translation_openai_endpoint";
    static final String PREF_DEEPSEEK_MODEL = "translation_deepseek_model";
    static final String PREF_DEEPSEEK_ENDPOINT = "translation_deepseek_endpoint";

    static final String DEFAULT_OPENAI_MODEL = "gpt-5-mini";
    static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/responses";
    static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash";
    static final String DEFAULT_DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions";

    private static final int MAX_BATCH_SEGMENTS=24,MAX_BATCH_CHARS=12000;
    private final ApiKeyStore keyStore;
    private final SharedPreferences prefs;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed=new AtomicBoolean(false);

    AiTranslationService(ApiKeyStore keyStore,SharedPreferences prefs){this.keyStore=keyStore;this.prefs=prefs;}

    static String normalizeProvider(String provider){return PROVIDER_DEEPSEEK.equals(provider)?PROVIDER_DEEPSEEK:PROVIDER_OPENAI;}
    static String providerLabel(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?"DeepSeek":"OpenAI";}
    static String defaultEndpoint(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?DEFAULT_DEEPSEEK_ENDPOINT:DEFAULT_OPENAI_ENDPOINT;}
    static String defaultModel(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?DEFAULT_DEEPSEEK_MODEL:DEFAULT_OPENAI_MODEL;}
    static String endpointPref(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?PREF_DEEPSEEK_ENDPOINT:PREF_OPENAI_ENDPOINT;}
    static String modelPref(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?PREF_DEEPSEEK_MODEL:PREF_OPENAI_MODEL;}

    String currentProvider(){return normalizeProvider(prefs.getString(PREF_PROVIDER,PROVIDER_OPENAI));}
    String configuredEndpoint(String provider){
        provider=normalizeProvider(provider);
        String fallback=PROVIDER_OPENAI.equals(provider)?prefs.getString(PREF_ENDPOINT,DEFAULT_OPENAI_ENDPOINT):DEFAULT_DEEPSEEK_ENDPOINT;
        String value=prefs.getString(endpointPref(provider),fallback);
        return value==null||value.trim().isEmpty()?defaultEndpoint(provider):value.trim();
    }
    String configuredModel(String provider){
        provider=normalizeProvider(provider);
        String fallback=PROVIDER_OPENAI.equals(provider)?prefs.getString(PREF_MODEL,DEFAULT_OPENAI_MODEL):DEFAULT_DEEPSEEK_MODEL;
        String value=prefs.getString(modelPref(provider),fallback);
        return value==null||value.trim().isEmpty()?defaultModel(provider):value.trim();
    }

    void translateMarkdown(String source,boolean targetArabic,TranslationService.Callback callback){
        if(closed.get()){callback.onError(new IllegalStateException("خدمة الترجمة مغلقة"));return;}
        final String provider=currentProvider();
        final String apiKey=keyStore.load(provider);
        if(apiKey.isEmpty()){callback.onError(new IllegalStateException("لم يتم حفظ مفتاح "+providerLabel(provider)+" بعد"));return;}
        final List<MarkdownTranslationPlan.Segment> segments=MarkdownTranslationPlan.segments(source,targetArabic);
        if(segments.isEmpty()){callback.onSuccess(source==null?"":source);return;}
        callback.onStatus("الاتصال بـ "+providerLabel(provider)+"…");
        executor.execute(()->{try{
            List<String> translated=new ArrayList<>(segments.size());int index=0;
            while(index<segments.size()){
                int start=index,chars=0;List<String> batch=new ArrayList<>();
                while(index<segments.size()&&batch.size()<MAX_BATCH_SEGMENTS){String text=segments.get(index).text;if(!batch.isEmpty()&&chars+text.length()>MAX_BATCH_CHARS)break;batch.add(text);chars+=text.length();index++;}
                if(batch.isEmpty()){batch.add(segments.get(index).text);index++;}
                callback.onStatus("ترجمة النص عبر "+providerLabel(provider)+"…");
                List<String> values=requestBatch(provider,apiKey,batch,targetArabic);
                if(values.size()!=batch.size())throw new IllegalStateException("عدد نتائج الترجمة لا يطابق النص المرسل");
                translated.addAll(values);callback.onProgress(index,segments.size());
                if(index<=start)throw new IllegalStateException("تعذر متابعة الترجمة");
            }
            callback.onSuccess(MarkdownTranslationPlan.apply(source,segments,translated));
        }catch(Exception e){callback.onError(e);}});
    }

    private List<String> requestBatch(String provider,String apiKey,List<String> batch,boolean targetArabic)throws Exception{
        String endpoint=configuredEndpoint(provider),model=configuredModel(provider);
        JSONArray inputSegments=new JSONArray();for(String value:batch)inputSegments.put(value);
        JSONObject userPayload=new JSONObject().put("target_language",targetArabic?"Arabic":"English").put("segments",inputSegments);
        String instructions="You are a precise translation engine. Translate every input segment to the requested target language. Keep commands, identifiers, paths, URLs, numbers, Markdown punctuation, and technical tokens unchanged unless they are ordinary prose. Return one translation for every segment in the same order. Do not add explanations.";
        if(PROVIDER_DEEPSEEK.equals(provider))return requestDeepSeek(endpoint,model,apiKey,userPayload,instructions,batch.size());
        return requestOpenAI(endpoint,model,apiKey,userPayload,instructions,batch.size());
    }

    private List<String> requestOpenAI(String endpoint,String model,String apiKey,JSONObject userPayload,String instructions,int expected)throws Exception{
        JSONObject itemSchema=new JSONObject().put("type","string");
        JSONObject translationsSchema=new JSONObject().put("type","array").put("items",itemSchema);
        JSONObject schema=new JSONObject().put("type","object").put("properties",new JSONObject().put("translations",translationsSchema)).put("required",new JSONArray().put("translations")).put("additionalProperties",false);
        JSONObject format=new JSONObject().put("type","json_schema").put("name","translation_batch").put("strict",true).put("schema",schema);
        JSONObject body=new JSONObject().put("model",model).put("instructions",instructions).put("input",userPayload.toString()).put("store",false).put("text",new JSONObject().put("format",format));
        JSONObject root=postJson(endpoint,apiKey,body);
        return translationsFromJson(extractOpenAIOutputText(root),expected);
    }

    private List<String> requestDeepSeek(String endpoint,String model,String apiKey,JSONObject userPayload,String instructions,int expected)throws Exception{
        String jsonInstruction=instructions+" Return ONLY a valid JSON object in this exact shape: {\"translations\":[\"...\"]}. The translations array length must exactly equal the input segments array length.";
        JSONArray messages=new JSONArray()
                .put(new JSONObject().put("role","system").put("content",jsonInstruction))
                .put(new JSONObject().put("role","user").put("content",userPayload.toString()));
        JSONObject body=new JSONObject()
                .put("model",model)
                .put("messages",messages)
                .put("thinking",new JSONObject().put("type","disabled"))
                .put("response_format",new JSONObject().put("type","json_object"))
                .put("stream",false);
        JSONObject root=postJson(endpoint,apiKey,body);
        JSONArray choices=root.optJSONArray("choices");
        if(choices==null||choices.length()==0)throw new JSONException("استجابة DeepSeek لا تحتوي على choices");
        JSONObject first=choices.optJSONObject(0);
        JSONObject message=first==null?null:first.optJSONObject("message");
        if(message==null)throw new JSONException("استجابة DeepSeek لا تحتوي على message");
        String content=message.optString("content","");
        if(content.trim().isEmpty())throw new JSONException("لم يُرجع DeepSeek نصًا مترجمًا");
        return translationsFromJson(content,expected);
    }

    private JSONObject postJson(String endpoint,String apiKey,JSONObject body)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint.trim()).openConnection();
        connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(70000);connection.setDoOutput(true);
        connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");
        byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=connection.getOutputStream()){out.write(payload);}
        int status=connection.getResponseCode();String response=readFully(status>=200&&status<300?connection.getInputStream():connection.getErrorStream());connection.disconnect();
        if(status<200||status>=300)throw new IllegalStateException(readApiError(status,response));
        return new JSONObject(response);
    }

    private static List<String> translationsFromJson(String raw,int expected)throws Exception{
        String text=raw==null?"":raw.trim();
        if(text.startsWith("```")){
            int firstNewline=text.indexOf('\n');int last=text.lastIndexOf("```");
            if(firstNewline>=0&&last>firstNewline)text=text.substring(firstNewline+1,last).trim();
        }
        JSONObject result=new JSONObject(text);JSONArray values=result.getJSONArray("translations");
        if(values.length()!=expected)throw new IllegalStateException("عدد نتائج الترجمة لا يطابق النص المرسل");
        List<String> translated=new ArrayList<>(values.length());for(int i=0;i<values.length();i++)translated.add(values.getString(i));return translated;
    }

    private static String extractOpenAIOutputText(JSONObject root)throws JSONException{
        JSONArray output=root.optJSONArray("output");if(output==null)throw new JSONException("استجابة API لا تحتوي على output");StringBuilder text=new StringBuilder();
        for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null)continue;JSONArray content=item.optJSONArray("content");if(content==null)continue;for(int j=0;j<content.length();j++){JSONObject part=content.optJSONObject(j);if(part==null)continue;if("output_text".equals(part.optString("type")))text.append(part.optString("text"));}}
        if(text.length()==0)throw new JSONException("لم تُرجع خدمة AI نصًا مترجمًا");return text.toString();
    }

    private static String readApiError(int status,String body){try{JSONObject root=new JSONObject(body==null?"{}":body);JSONObject error=root.optJSONObject("error");String message=error==null?"":error.optString("message");if(!message.isEmpty())return "API "+status+": "+message;}catch(Exception ignored){}return "فشل طلب الترجمة (HTTP "+status+")";}
    private static String readFully(InputStream stream)throws Exception{if(stream==null)return "";StringBuilder out=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)out.append(line).append('\n');}return out.toString();}
    void shutdown(){closed.set(true);executor.shutdownNow();}
}
