package dev.atlas.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** OpenAI-compatible transport, also used by local LM Studio servers. No game-thread work here. */
public final class AtlasAiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    public record Completion(JsonObject result, String raw, String format, int attempts) {}
    public static final class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
    }
    private AtlasAiClient() {}

    public static CompletableFuture<Completion> send(AtlasConfig config, JsonArray messages, String key) {
        List<String> formats = config.aiResponseFormat.equals("auto")
                ? List.of("json_object", "json_schema", "prompt") : List.of(config.aiResponseFormat);
        long deadline = System.nanoTime() + Duration.ofSeconds(config.aiTimeoutSeconds).toNanos();
        return attempt(config, messages, key, formats, 0, deadline);
    }
    private static CompletableFuture<Completion> attempt(AtlasConfig config, JsonArray messages, String key,
            List<String> formats, int index, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return CompletableFuture.failedFuture(new HttpTimeoutException("AI deadline exceeded"));
        String format = formats.get(index);
        JsonObject body = new JsonObject();
        body.addProperty("model", config.aiModel); body.add("messages", messages);
        body.addProperty("max_tokens", 1800); body.addProperty("stream", false);
        if (!format.equals("prompt")) body.add("response_format", responseFormat(format));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.aiEndpoint))
                .timeout(Duration.ofNanos(remaining))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() != 200) {
                // Retry only an explicit parameter rejection: no generation/action succeeded yet.
                if (index + 1 < formats.size() && rejectsFormat(response.statusCode(), response.body()))
                    return attempt(config, messages, key, formats, index + 1, deadline);
                return CompletableFuture.failedFuture(httpFailure(response.statusCode(), response.body(), key));
            }
            try { return CompletableFuture.completedFuture(parse(response.body(), format, index + 1)); }
            catch (Failure invalid) { return CompletableFuture.failedFuture(invalid); }
        });
    }
    static JsonObject responseFormat(String format) {
        JsonObject result = new JsonObject(); result.addProperty("type", format);
        if (format.equals("json_schema")) {
            JsonObject properties = new JsonObject();
            JsonObject reply = new JsonObject(); reply.addProperty("type", "string"); properties.add("reply", reply);
            JsonObject action = new JsonObject(); action.addProperty("type", "object");
            JsonObject actionProperties = new JsonObject();
            JsonObject type = new JsonObject(); type.addProperty("type", "string"); actionProperties.add("type", type);
            action.add("properties", actionProperties); action.add("required", strings("type"));
            action.addProperty("additionalProperties", true);
            JsonObject actions = new JsonObject(); actions.addProperty("type", "array"); actions.addProperty("maxItems", 3);
            actions.add("items", action); properties.add("actions", actions);
            JsonObject schema = new JsonObject(); schema.addProperty("type", "object"); schema.add("properties", properties);
            schema.add("required", strings("reply", "actions")); schema.addProperty("additionalProperties", false);
            JsonObject wrapper = new JsonObject(); wrapper.addProperty("name", "atlas_reply"); wrapper.add("schema", schema);
            result.add("json_schema", wrapper);
        }
        return result;
    }
    private static JsonArray strings(String... values) {
        JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result;
    }
    static boolean rejectsFormat(int status, String body) {
        if (status != 400 && status != 422) return false;
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return text.contains("response_format") || text.contains("json_object") || text.contains("json_schema")
                || text.contains("structured output") || (text.contains("unsupported") && text.contains("format"));
    }
    static Completion parse(String body, String format, int attempts) {
        try {
            if (body == null || body.length() > 131072) throw new IllegalArgumentException();
            JsonObject envelope = JsonParser.parseString(body).getAsJsonObject();
            JsonObject choice = envelope.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    && "length".equals(choice.get("finish_reason").getAsString()))
                throw new Failure("AI обрезал ответ по лимиту токенов. Сократи просьбу или отключи thinking в LM Studio.");
            String raw = choice.getAsJsonObject("message").get("content").getAsString().strip();
            if (raw.startsWith("<think>")) {
                int end = raw.indexOf("</think>");
                if (end < 0) throw new IllegalArgumentException();
                raw = raw.substring(end + 8).strip();
            }
            if (raw.startsWith("```") && raw.endsWith("```")) {
                int newline = raw.indexOf('\n');
                if (newline < 0) throw new IllegalArgumentException();
                raw = raw.substring(newline + 1, raw.length() - 3).strip();
            }
            JsonObject result = JsonParser.parseString(raw).getAsJsonObject();
            if (!result.has("reply") || !result.get("reply").isJsonPrimitive()
                    || !result.getAsJsonPrimitive("reply").isString()) throw new IllegalArgumentException();
            if (!result.has("actions")) result.add("actions", new JsonArray());
            JsonArray actions = result.getAsJsonArray("actions");
            if (actions.size() > 3) throw new IllegalArgumentException();
            for (JsonElement element : actions) {
                JsonObject action = element.getAsJsonObject();
                if (!action.has("type") || !action.get("type").isJsonPrimitive()
                        || !action.getAsJsonPrimitive("type").isString()) throw new IllegalArgumentException();
            }
            return new Completion(result, result.toString(), format, attempts);
        } catch (Failure failed) { throw failed; }
        catch (RuntimeException invalid) {
            throw new Failure("AI ответил, но не вернул JSON Атласа. Попробуй формат ответа json_schema в Vibe Coding.");
        }
    }
    static Failure httpFailure(int status, String body, String key) {
        String advice = switch (status) {
            case 401, 403 -> "Проверь API-ключ и авторизацию в LM Studio.";
            case 404 -> "Проверь адрес /v1/chat/completions и идентификатор модели.";
            case 429 -> "Сервер занят или исчерпан лимит запросов.";
            default -> rejectsFormat(status, body) ? "Сервер не поддерживает выбранный формат ответа. Выбери auto или prompt."
                    : "Проверь модель, длину контекста и лог AI-сервера.";
        };
        String detail = "";
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonElement error = root.has("error") ? root.get("error") : root.get("message");
            if (error != null && error.isJsonObject()) error = error.getAsJsonObject().get("message");
            if (error != null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString()) detail = error.getAsString();
        } catch (RuntimeException ignored) {}
        if (key != null && !key.isEmpty()) detail = detail.replace(key, "[ключ скрыт]");
        detail = detail.replaceAll("(?i)bearer\\s+\\S+", "Bearer [скрыто]").replaceAll("[\\p{Cntrl}\\p{Cf}]", " ");
        detail = detail.substring(0, Math.min(240, detail.length()));
        return new Failure("AI вернул HTTP " + status + ". " + advice + (detail.isBlank() ? "" : " Причина: " + detail));
    }
    public static String explain(Throwable error, int timeout) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof Failure) return cause.getMessage();
        if (cause instanceof HttpTimeoutException)
            return "AI не успел ответить за " + timeout + " сек. Увеличь тайм-аут в Vibe Coding, например до 180–300 сек.";
        if (cause instanceof ConnectException)
            return "Не удалось подключиться к AI-серверу. Проверь адрес, порт и доступ по локальной сети в LM Studio.";
        return "Соединение с AI прервано (" + cause.getClass().getSimpleName() + "). Проверь лог Minecraft и LM Studio.";
    }
}
