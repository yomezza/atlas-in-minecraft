package dev.atlas.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtlasAiClientTest {
    private static final String KEY = "fake-fixture-key";
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final AtlasConfig config = new AtlasConfig();
        Server(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", handler::handle); server.start();
            config.aiEndpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
            config.aiModel = "qwen3-4b-instruct";
            config.aiTimeoutSeconds = 3;
        }
        @Override public void close() { server.stop(0); }
    }
    private static JsonArray messages() {
        JsonArray messages = new JsonArray(); JsonObject message = new JsonObject();
        message.addProperty("role", "user"); message.addProperty("content", "Здарова"); messages.add(message); return messages;
    }
    private static JsonObject request(HttpExchange exchange) throws IOException {
        return JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private static String envelope(String content, String finish) {
        JsonObject message = new JsonObject(); message.addProperty("content", content);
        JsonObject choice = new JsonObject(); choice.add("message", message); choice.addProperty("finish_reason", finish);
        JsonArray choices = new JsonArray(); choices.add(choice); JsonObject response = new JsonObject(); response.add("choices", choices); return response.toString();
    }
    @Test void lmStudioRejectionAutomaticallySwitchesToSchema() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            JsonObject body = request(exchange); int attempt = calls.incrementAndGet();
            assertEquals("Bearer " + KEY, exchange.getRequestHeaders().getFirst("Authorization"));
            assertFalse(body.get("stream").getAsBoolean());
            assertEquals("qwen3-4b-instruct", body.get("model").getAsString());
            assertEquals(messages(), body.getAsJsonArray("messages"));
            JsonObject format = body.getAsJsonObject("response_format");
            if (attempt == 1) {
                assertEquals("json_object", format.get("type").getAsString());
                respond(exchange, 400, "{\"error\":\"response_format.type must be text or json_schema\"}");
            } else {
                assertEquals("json_schema", format.get("type").getAsString());
                assertTrue(format.getAsJsonObject("json_schema").getAsJsonObject("schema").getAsJsonObject("properties").has("actions"));
                respond(exchange, 200, envelope("{\"reply\":\"Привет, напарник.\",\"actions\":[]}", "stop"));
            }
        })) {
            var result = AtlasAiClient.send(fixture.config, messages(), KEY).join();
            assertEquals("json_schema", result.format()); assertEquals(2, result.attempts()); assertEquals(2, calls.get());
            assertEquals("Привет, напарник.", result.result().get("reply").getAsString());
        }
    }
    @Test void explicitSchemaUsesOneRequestAndRetainsActionArguments() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            calls.incrementAndGet(); assertEquals("json_schema", request(exchange).getAsJsonObject("response_format").get("type").getAsString());
            respond(exchange, 200, envelope("{\"reply\":\"Хорошо.\",\"actions\":[{\"type\":\"commands\",\"commands\":[\"time set day\"]}]}", "stop"));
        })) {
            fixture.config.aiResponseFormat = "json_schema";
            var result = AtlasAiClient.send(fixture.config, messages(), KEY).join();
            assertEquals(1, calls.get());
            assertEquals("time set day", result.result().getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonArray("commands").get(0).getAsString());
        }
    }
    @Test void schemaRejectionFallsBackToPromptAndAcceptsQwenWrapper() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            JsonObject body = request(exchange); int attempt = calls.incrementAndGet();
            if (attempt < 3) respond(exchange, 422, "{\"error\":{\"message\":\"response_format is unsupported\"}}");
            else {
                assertFalse(body.has("response_format"));
                respond(exchange, 200, envelope("<think>hidden reasoning</think>\n```json\n{\"reply\":\"Я рядом.\",\"actions\":[]}\n```", "stop"));
            }
        })) {
            var result = AtlasAiClient.send(fixture.config, messages(), KEY).join();
            assertEquals(3, calls.get()); assertEquals("prompt", result.format()); assertFalse(result.raw().contains("hidden reasoning"));
        }
    }
    @Test void authorizationErrorIsNotRetriedAndDoesNotExposeKey() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            request(exchange); calls.incrementAndGet();
            respond(exchange, 401, "{\"error\":{\"message\":\"Invalid Bearer " + KEY + "\"}}");
        })) {
            CompletionException failure = assertThrows(CompletionException.class, () -> AtlasAiClient.send(fixture.config, messages(), KEY).join());
            String message = AtlasAiClient.explain(failure, 120);
            assertEquals(1, calls.get()); assertTrue(message.contains("HTTP 401")); assertFalse(message.contains(KEY));
        }
    }
    @Test void modelErrorDoesNotTriggerFormatRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            request(exchange); calls.incrementAndGet(); respond(exchange, 400, "{\"error\":\"Model not found\"}");
        })) {
            CompletionException failure = assertThrows(CompletionException.class, () -> AtlasAiClient.send(fixture.config, messages(), KEY).join());
            assertEquals(1, calls.get()); assertTrue(AtlasAiClient.explain(failure, 120).contains("Model not found"));
        }
    }
    @Test void successfulButInvalidGenerationIsNeverRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            request(exchange); calls.incrementAndGet(); respond(exchange, 200, envelope("Привет!", "stop"));
        })) {
            CompletionException failure = assertThrows(CompletionException.class, () -> AtlasAiClient.send(fixture.config, messages(), KEY).join());
            assertEquals(1, calls.get()); assertTrue(AtlasAiClient.explain(failure, 120).contains("JSON Атласа"));
        }
    }
    @Test void truncatedAndMalformedActionsCannotBecomeExecutableReplies() {
        assertThrows(AtlasAiClient.Failure.class, () -> AtlasAiClient.parse(envelope("{\"reply\":\"Привет\",\"actions\":[]}", "length"), "json_schema", 1));
        assertThrows(AtlasAiClient.Failure.class, () -> AtlasAiClient.parse(envelope("{\"reply\":\"Привет\",\"actions\":[{}]}", "stop"), "json_schema", 1));
        assertThrows(AtlasAiClient.Failure.class, () -> AtlasAiClient.parse("{\"error\":\"No model loaded\"}", "json_schema", 1));
        assertTrue(AtlasAiClient.explain(new CompletionException(new HttpTimeoutException("test timeout")), 180).contains("180 сек"));
    }
    @Test void actualTimeoutDoesNotRetryGeneration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server fixture = new Server(exchange -> {
            request(exchange); calls.incrementAndGet();
            try { Thread.sleep(1500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            try { respond(exchange, 200, envelope("{\"reply\":\"Привет\",\"actions\":[]}", "stop")); } catch (IOException disconnected) { exchange.close(); }
        })) {
            fixture.config.aiTimeoutSeconds = 1;
            CompletionException failure = assertThrows(CompletionException.class, () -> AtlasAiClient.send(fixture.config, messages(), KEY).join());
            assertEquals(1, calls.get()); assertTrue(AtlasAiClient.explain(failure, 1).contains("не успел ответить"));
        }
    }
}
