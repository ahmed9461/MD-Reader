package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ApiKeyStore {
    static final String PROVIDER_OPENAI = "openai";
    static final String PROVIDER_DEEPSEEK = "deepseek";

    private static final String STORE = "md_reader_secrets";
    private static final String OPENAI_VALUE = "openai_api_key_ciphertext";
    private static final String DEEPSEEK_VALUE = "deepseek_api_key_ciphertext";
    private static final String OPENAI_ALIAS = "md_reader_openai_key_v1";
    private static final String DEEPSEEK_ALIAS = "md_reader_deepseek_key_v1";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private final SharedPreferences prefs;

    ApiKeyStore(Context context) { prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE); }

    synchronized void save(String provider, String apiKey) throws Exception {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) { clear(provider); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias(provider)));
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!prefs.edit().putString(value(provider), packed).commit()) throw new IllegalStateException("تعذر حفظ مفتاح API");
    }

    synchronized String load(String provider) {
        String packed = prefs.getString(value(provider), "");
        if (packed == null || packed.isEmpty()) return "";
        try {
            String[] parts = packed.split(":", 2);
            if (parts.length != 2) throw new IllegalStateException("Invalid secret payload");
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(alias(provider)), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            prefs.edit().remove(value(provider)).apply();
            return "";
        }
    }

    synchronized boolean hasKey(String provider) { return !load(provider).isEmpty(); }
    synchronized void clear(String provider) { prefs.edit().remove(value(provider)).apply(); }
    String masked(String provider) { String key=load(provider); if(key.isEmpty()) return "غير محفوظ"; int tail=Math.min(4,key.length()); return "••••••••"+key.substring(key.length()-tail); }

    synchronized void save(String apiKey) throws Exception { save(PROVIDER_OPENAI, apiKey); }
    synchronized String load() { return load(PROVIDER_OPENAI); }
    synchronized boolean hasKey() { return hasKey(PROVIDER_OPENAI); }
    synchronized void clear() { clear(PROVIDER_OPENAI); }
    String masked() { return masked(PROVIDER_OPENAI); }

    private static boolean deepSeek(String provider) { return PROVIDER_DEEPSEEK.equals(provider); }
    private static String value(String provider) { return deepSeek(provider) ? DEEPSEEK_VALUE : OPENAI_VALUE; }
    private static String alias(String provider) { return deepSeek(provider) ? DEEPSEEK_ALIAS : OPENAI_ALIAS; }

    private SecretKey getOrCreateKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE); keyStore.load(null);
        java.security.Key existing = keyStore.getKey(alias, null); if(existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build();
        generator.init(spec); return generator.generateKey();
    }
}
