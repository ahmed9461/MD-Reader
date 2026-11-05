package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/** Exercises credential selection and routing with local responses and dummy credentials only. */
final class ProviderRegressionCheck {
    static void run(Context context, SpeechReader reader, BiConsumer<Boolean,String> check) throws Exception {
        String router = ApiKeyStore.PROVIDER_OPENROUTER, deep = ApiKeyStore.PROVIDER_DEEPSEEK;
        ApiKeyStore keys = new ApiKeyStore(context);
        SharedPreferences prefs = context.getSharedPreferences("provider-regression", Context.MODE_PRIVATE);
        SharedPreferences vault = context.getSharedPreferences("md_reader_secrets", Context.MODE_PRIVATE);
        prefs.edit().clear().putString(AiTranslationService.PREF_PROVIDER, "openai")
                .putString("translation_openai_model", "old-direct-model").putString("translation_ai_endpoint", "https://old.invalid").commit();
        vault.edit().putString("openai_api_key_ciphertext","old-ciphertext").commit();
        keys.clear(router);
        keys.save(deep, "dummy-deep");
        List<String> urls = new ArrayList<>(), auth = new ArrayList<>();
        List<JSONObject> requests = new ArrayList<>();
        List<Boolean> redirects = new ArrayList<>();
        AiTranslationService service = new AiTranslationService(keys,prefs,(url,key,body,follow)->{
            urls.add(url);auth.add(key);requests.add(body);redirects.add(follow);
            JSONObject segmentBody = new JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"));
            JSONArray translations = new JSONArray();
            for (int i=0; i<segmentBody.getJSONArray("segments").length(); i++) translations.put("مرحبا");
            String payload = new JSONObject().put("translations",translations).toString();
            JSONObject message = new JSONObject().put("message",new JSONObject().put("content",payload));
            return new JSONObject().put("model","returned-model-"+urls.size()).put("choices",new JSONArray().put(message));
        });
        try {
            check.accept(service.currentProvider().equals(router) && !keys.hasKey(router), "upgrade isolates old OpenAI credentials");
            check.accept(service.configuredModel(router).equals("openai/gpt-4o-mini"), "upgrade ignores old direct model");
            keys.save(router,"dummy-router");
            check.accept(reader.hasOpenRouterKey() && keys.load(deep).equals("dummy-deep"), "speech and translation share only the router key");
            check.accept(!vault.getString("openrouter_api_key_ciphertext","").contains("dummy-router"), "router key encrypted at rest");
            prefs.edit().putString(AiTranslationService.PREF_OPENROUTER_MODEL,"chosen/model").commit();
            String[] result = translate(service,String.join("\n",java.util.Collections.nCopies(25,"Hello")));
            check.accept(urls.size()==2 && urls.stream().allMatch(AiTranslationService.DEFAULT_OPENROUTER_ENDPOINT::equals), "all batches use fixed OpenRouter endpoint");
            check.accept(auth.stream().allMatch("dummy-router"::equals) && !redirects.contains(true), "router credential never follows redirects");
            check.accept(requests.get(0).getString("model").equals("chosen/model") && !requests.get(0).has("thinking"), "OpenRouter chat payload uses chosen model");
            check.accept(result[1].equals("returned-model-1، returned-model-2") && result[0].equals(String.join("\n",java.util.Collections.nCopies(25,"مرحبا"))), "model provenance across batches is separate from translated text");

            String deepUrl="https://custom-deepseek.invalid/chat/completions";
            prefs.edit().putString(AiTranslationService.PREF_PROVIDER,deep)
                    .putString(AiTranslationService.PREF_DEEPSEEK_ENDPOINT,deepUrl).putString(AiTranslationService.PREF_DEEPSEEK_MODEL,"deepseek-custom").commit();
            result=translate(service,"Hello");
            check.accept(urls.get(2).equals(deepUrl) && auth.get(2).equals("dummy-deep"), "DeepSeek keeps its endpoint and key");
            check.accept(requests.get(2).getString("model").equals("deepseek-custom") && requests.get(2).getJSONObject("thinking").getString("type").equals("disabled"), "DeepSeek direct payload retained");
            check.accept(result[1].equals("returned-model-3"), "DeepSeek reports its response model");
            reader.setEnginePreference(SpeechReader.ENGINE_AI);
            reader.setModel(OpenRouterSpeechClient.MULTILINGUAL_MODEL);
            check.accept(reader.state().model.equals(OpenRouterSpeechClient.MULTILINGUAL_MODEL), "player reports active speech model");
            JSONObject speech = OpenRouterSpeechClient.requestBody("مرحبا",reader.model(),"george");
            check.accept(speech.getString("model").equals(reader.model()) && speech.getString("voice").equals("george")
                    && speech.getString("response_format").equals("mp3") && !speech.has("speed"), "cloud TTS requests the chosen model, voice and playable format");
            keys.clear(router);
            reader.setEnginePreference(SpeechReader.ENGINE_AUTO);
            check.accept(reader.state().engine.equals(SpeechReader.ENGINE_DEVICE) && reader.state().model.isEmpty() && keys.hasKey(deep), "automatic device fallback has no cloud model and preserves DeepSeek");
        } finally {
            service.shutdown();keys.clear(router);keys.clear(deep);
            vault.edit().remove("openai_api_key_ciphertext").commit();prefs.edit().clear().commit();
        }
    }

    private static String[] translate(AiTranslationService service,String text) throws Exception {
        CountDownLatch done=new CountDownLatch(1);
        String[] output=new String[2]; Exception[] failure=new Exception[1];
        service.translateMarkdown(text,true,new AiTranslationService.Callback(){
            public void onStatus(String status){}
            public void onProgress(int completed,int total){}
            public void onSuccess(String value,String model){output[0]=value;output[1]=model;done.countDown();}
            public void onError(Exception e){failure[0]=e;done.countDown();}
        });
        if (!done.await(5,TimeUnit.SECONDS)) throw new AssertionError("local chat fixture timed out");
        if (failure[0]!=null) throw failure[0];
        return output;
    }
}
