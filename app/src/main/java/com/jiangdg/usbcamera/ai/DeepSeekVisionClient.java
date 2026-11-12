package com.jiangdg.usbcamera.ai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal DeepSeek OpenAI-compatible vision client.
 *
 * It uses HttpURLConnection and Android's built-in org.json so this feature
 * does not add another networking or JSON dependency to the APK.
 */
public final class DeepSeekVisionClient {
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 90000;
    private static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;

    private DeepSeekVisionClient() {
    }

    public static String analyze(File imageFile, AiSettings.Config config)
            throws IOException, JSONException {
        if (config == null || config.apiKey.isEmpty()) {
            throw new IOException("请先在 AI 设置中填写 DeepSeek API Key");
        }
        if (imageFile == null || !imageFile.isFile()) {
            throw new IOException("拍照文件不存在");
        }
        if (imageFile.length() > MAX_IMAGE_BYTES) {
            throw new IOException("图片过大，DeepSeek 单张图片上限为 32 MiB");
        }

        String imageBase64 = Base64.encodeToString(readFile(imageFile), Base64.NO_WRAP);
        JSONObject imageUrl = new JSONObject()
                .put("url", "data:image/jpeg;base64," + imageBase64)
                .put("detail", "low");

        JSONArray content = new JSONArray()
                .put(new JSONObject()
                        .put("type", "text")
                        .put("text", config.prompt))
                .put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", imageUrl));

        JSONObject userMessage = new JSONObject()
                .put("role", "user")
                .put("content", content);

        JSONObject payload = new JSONObject()
                .put("model", config.model)
                .put("messages", new JSONArray().put(userMessage))
                .put("stream", false)
                .put("temperature", 0.3)
                .put("max_tokens", 800);

        byte[] requestBody = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(config.endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(requestBody.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
                outputStream.flush();
            }

            int statusCode = connection.getResponseCode();
            InputStream responseStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readStream(responseStream);

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("DeepSeek HTTP " + statusCode + ": "
                        + extractErrorMessage(responseBody));
            }

            JSONObject response = new JSONObject(responseBody);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IOException("DeepSeek 未返回 choices: " + shorten(responseBody));
            }

            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IOException("DeepSeek 返回格式不完整: " + shorten(responseBody));
            }
            String answer = message.optString("content", "").trim();
            if (answer.isEmpty()) {
                throw new IOException("DeepSeek 返回了空文本");
            }
            return answer;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream;
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString("UTF-8");
        }
    }

    private static String extractErrorMessage(String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONObject error = response.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (JSONException ignored) {
            // Fall back to the raw response below.
        }
        return shorten(responseBody);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
