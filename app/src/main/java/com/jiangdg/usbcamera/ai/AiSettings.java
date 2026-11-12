package com.jiangdg.usbcamera.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Local configuration for the DeepSeek vision request.
 *
 * The API key is stored only in the app's private SharedPreferences file.
 * It is never written to logs or source control.
 */
public final class AiSettings {
    private static final String PREFS_NAME = "deepseek_ai_settings";

    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_PROMPT = "prompt";
    private static final String KEY_MODEL = "model";
    private static final String KEY_ENDPOINT = "endpoint";

    public static final String DEFAULT_MODEL = "deepseek-flash";
    public static final String DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_PROMPT =
            "请用简短、自然的中文描述这张图片中的主要内容，并指出明显的异常或值得注意的地方。"
            + "回答控制在80字以内，适合直接语音播报。";

    private AiSettings() {
    }

    public static Config load(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new Config(
                preferences.getString(KEY_API_KEY, ""),
                preferences.getString(KEY_PROMPT, DEFAULT_PROMPT),
                preferences.getString(KEY_MODEL, DEFAULT_MODEL),
                preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
        );
    }

    public static void save(Context context, Config config) {
        SharedPreferences.Editor editor = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit();
        editor.putString(KEY_API_KEY, safe(config.apiKey));
        editor.putString(KEY_PROMPT, safe(config.prompt));
        editor.putString(KEY_MODEL, safe(config.model));
        editor.putString(KEY_ENDPOINT, safe(config.endpoint));
        editor.apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Config {
        public final String apiKey;
        public final String prompt;
        public final String model;
        public final String endpoint;

        public Config(String apiKey, String prompt, String model, String endpoint) {
            this.apiKey = safe(apiKey);
            this.prompt = TextUtils.isEmpty(prompt) ? DEFAULT_PROMPT : prompt.trim();
            this.model = TextUtils.isEmpty(model) ? DEFAULT_MODEL : model.trim();
            this.endpoint = TextUtils.isEmpty(endpoint) ? DEFAULT_ENDPOINT : endpoint.trim();
        }
    }
}
