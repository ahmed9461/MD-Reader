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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AiTranslationService {
    static final String PREF_PROVIDER = "translation_ai_provider";
    static final String PROVIDER_OPENROUTER = ApiKeyStore.PROVIDER_OPENROUTER;
    static final String PROVIDER_DEEPSEEK = ApiKeyStore.PROVIDER_DEEPSEEK;

    static final String PREF_OPENROUTER_MODEL = "translation_openrouter_model";
    static final String PREF_DEEPSEEK_MODEL = "translation_deepseek_model";
    static final String PREF_DEEPSEEK_ENDPOINT = "translation_deepseek_endpoint";
    static final String DEFAULT_OPENROUTER_MODEL = "openai/gpt-4o-mini";
    static final String DEFAULT_OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash";
    static final String DEFAULT_DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions";

    private static final int MAX_BATCH_SEGMENTS=24,MAX_BATCH_CHARS=12000;
    interface ChatTransport {
        JSONObject post(String endpoint,String apiKey,JSONObject body,boolean allowRedirects) throws Exception;
    }
    private final ChatTransport transport;
    private final ApiKeyStore keyStore;
    private final SharedPreferences prefs;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed=new AtomicBoolean(false);

    AiTranslationService(ApiKeyStore keyStore, SharedPreferences prefs) {
        this(keyStore, prefs, AiTranslationService::postJson);
    }
    AiTranslationService(ApiKeyStore keyStore, SharedPreferences prefs, ChatTransport transport) {
        this.keyStore=keyStore;this.prefs=prefs;this.transport=transport;
    }

    static String normalizeProvider(String provider){return PROVIDER_DEEPSEEK.equals(provider)?PROVIDER_DEEPSEEK:PROVIDER_OPENROUTER;}
    static String providerLabel(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?"DeepSeek":"OpenRouter";}
    static String defaultEndpoint(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?DEFAULT_DEEPSEEK_ENDPOINT:DEFAULT_OPENROUTER_ENDPOINT;}
    static String defaultModel(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?DEFAULT_DEEPSEEK_MODEL:DEFAULT_OPENROUTER_MODEL;}
    static String modelPref(String provider){return PROVIDER_DEEPSEEK.equals(normalizeProvider(provider))?PREF_DEEPSEEK_MODEL:PREF_OPENROUTER_MODEL;}

    String currentProvider(){return normalizeProvider(prefs.getString(PREF_PROVIDER,PROVIDER_OPENROUTER));}
    String configuredEndpoint(String provider){
        if (!PROVIDER_DEEPSEEK.equals(provider)) return DEFAULT_OPENROUTER_ENDPOINT;
        String value = prefs.getString(PREF_DEEPSEEK_ENDPOINT, DEFAULT_DEEPSEEK_ENDPOINT);
        return value==null||value.trim().isEmpty()?defaultEndpoint(provider):value.trim();
    }
    String configuredModel(String provider){
        provider=normalizeProvider(provider);
        String value=prefs.getString(modelPref(provider),defaultModel(provider));
        return value==null||value.trim().isEmpty()?defaultModel(provider):value.trim();
    }

    interface Callback {
        void onStatus(String status);
        void onProgress(int completed, int total);
        void onSuccess(String translated, String reportedModel);
        void onError(Exception error);
    }

    void translateMarkdown(String source,boolean targetArabic,Callback callback){
        if(closed.get()){callback.onError(new IllegalStateException("خدمة الترجمة مغلقة"));return;}
        final String provider=currentProvider();
        final String model = configuredModel(provider);
        final String endpoint = configuredEndpoint(provider);
        final String apiKey=keyStore.load(provider);
        if(apiKey.isEmpty()){callback.onError(new IllegalStateException("لم يتم حفظ مفتاح "+providerLabel(provider)+" بعد"));return;}
        final List<MarkdownTranslationPlan.Segment> segments=MarkdownTranslationPlan.segments(source,targetArabic);
        if(segments.isEmpty()){callback.onSuccess(source==null?"":source,"");return;}
        callback.onStatus("الاتصال بـ "+providerLabel(provider)+"…");
        executor.execute(()->{try{
            Set<String> reportedModels = new LinkedHashSet<>();
            List<String> translated=new ArrayList<>(segments.size());int index=0;
            while(index<segments.size()){
                int start=index,chars=0;List<String> batch=new ArrayList<>();
                while(index<segments.size()&&batch.size()<MAX_BATCH_SEGMENTS){String text=segments.get(index).text;if(!batch.isEmpty()&&chars+text.length()>MAX_BATCH_CHARS)break;batch.add(text);chars+=text.length();index++;}
                if(batch.isEmpty()){batch.add(segments.get(index).text);index++;}
                callback.onStatus("ترجمة النص عبر "+providerLabel(provider)+" · "+model);
                JSONObject response = transport.post(endpoint, apiKey, buildRequest(provider, model, batch, targetArabic), !PROVIDER_OPENROUTER.equals(provider));
                List<String> values = translatedValues(response, batch.size());
                String reported = response.isNull("model") ? "" : response.optString("model").trim();
                if (!reported.isEmpty()) reportedModels.add(reported);
                translated.addAll(values);callback.onProgress(index,segments.size());
                if(index<=start)throw new IllegalStateException("تعذر متابعة الترجمة");
            }
            callback.onSuccess(MarkdownTranslationPlan.apply(source,segments,translated),String.join("، ", reportedModels));
        }catch(Exception e){callback.onError(e);}});
    }

    static JSONObject buildRequest(String provider,String model,List<String> batch,boolean targetArabic)throws JSONException{
        JSONArray inputSegments=new JSONArray();for(String value:batch)inputSegments.put(value);
        JSONObject userPayload=new JSONObject().put("target_language",targetArabic?"Arabic":"English").put("segments",inputSegments);
        String instructions="You are a precise translation engine. Translate every input segment to the requested target language. Keep commands, identifiers, paths, URLs, numbers, Markdown punctuation, and technical tokens unchanged unless they are ordinary prose. Return one translation for every segment in the same order. Do not add explanations.";
        String jsonInstruction=instructions+" Return ONLY a valid JSON object in this exact shape: {\"translations\":[\"...\"]}. The translations array length must exactly equal the input segments array length.";
        JSONArray messages=new JSONArray()
                .put(new JSONObject().put("role","system").put("content",jsonInstruction))
                .put(new JSONObject().put("role","user").put("content",userPayload.toString()));
        JSONObject body=new JSONObject()
                .put("model",model)
                .put("messages",messages)
                .put("response_format",new JSONObject().put("type","json_object"))
                .put("stream",false);
        if (PROVIDER_DEEPSEEK.equals(provider)) body.put("thinking",new JSONObject().put("type","disabled"));
        return body;
    }

    static List<String> translatedValues(JSONObject root,int expected)throws Exception{
        JSONArray choices=root.optJSONArray("choices");
        if(choices==null||choices.length()==0)throw new JSONException("استجابة المزوّد لا تحتوي على choices");
        JSONObject first=choices.optJSONObject(0);
        JSONObject message=first==null?null:first.optJSONObject("message");
        if(message==null)throw new JSONException("استجابة المزوّد لا تحتوي على message");
        String content=message.optString("content","");
        if(content.trim().isEmpty())throw new JSONException("لم يُرجع المزوّد نصًا مترجمًا");
        return translationsFromJson(content,expected);
    }

    private static JSONObject postJson(String endpoint,String apiKey,JSONObject body,boolean allowRedirects)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(allowRedirects);
            connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(70000);connection.setDoOutput(true);
            connection.setRequestProperty("Authorization","Bearer "+apiKey);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setRequestProperty("Accept","application/json");
            byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=connection.getOutputStream()){out.write(payload);}
            int status=connection.getResponseCode();
            String response=readFully(status>=200&&status<300?connection.getInputStream():connection.getErrorStream());
            if(status<200||status>=300)throw new IllegalStateException(readApiError(status,response).replace(apiKey,"••••"));
            return new JSONObject(response);
        } finally {
            connection.disconnect();
        }
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

    private static String readApiError(int status,String body){try{JSONObject root=new JSONObject(body==null?"{}":body);JSONObject error=root.optJSONObject("error");String message=error==null?"":error.optString("message");if(!message.isEmpty())return "API "+status+": "+message;}catch(Exception ignored){}return "فشل طلب الترجمة (HTTP "+status+")";}
    private static String readFully(InputStream stream)throws Exception{if(stream==null)return "";StringBuilder out=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)out.append(line).append('\n');}return out.toString();}
    void shutdown(){closed.set(true);executor.shutdownNow();}
}
