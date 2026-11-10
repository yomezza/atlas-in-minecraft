package dev.atlas.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** External requests never block the game thread. Credentials are never included in chat or prompts. */
public final class AtlasAi {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson GSON = new Gson();
    private static final Set<UUID> PENDING = new HashSet<>();
    private static final Map<UUID, Deque<JsonObject>> MEMORY = new HashMap<>();
    private static long session;
    private AtlasAi() {}
    public static void ask(ServerPlayerEntity player, String input) {
        UUID owner = player.getUuid();
        if (PENDING.contains(owner)) { AtlasManager.say(player, "Секунду, ещё думаю над предыдущей просьбой."); return; }
        String key = AtlasMod.CONFIG.effectiveApiKey();
        if (key == null || key.isBlank()) {
            if (fallback(player, input)) return;
            AtlasManager.say(player, "Укажи API-ключ в Vibe Coding → Атлас или в config/atlas-companion.json. Встроенные просьбы работают."); return;
        }
        try {
            URI endpoint = URI.create(AtlasMod.CONFIG.aiEndpoint);
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !"http".equalsIgnoreCase(endpoint.getScheme()))
                throw new IllegalArgumentException("AI endpoint must use HTTP or HTTPS");
            JsonArray messages = new JsonArray();
            messages.add(message("system", systemPrompt()));
            if (!AtlasMod.CONFIG.aiSystemPrompt.isBlank())
                messages.add(message("system", AtlasMod.CONFIG.aiSystemPrompt));
            Deque<JsonObject> history = MEMORY.computeIfAbsent(owner, id -> new ArrayDeque<>());
            history.forEach(messages::add);
            String bounded = input.substring(0, Math.min(input.length(), 800));
            JsonObject user = message("user", bounded);
            messages.add(user);
            JsonObject body = new JsonObject();
            body.addProperty("model", AtlasMod.CONFIG.aiModel);
            body.add("messages", messages); body.addProperty("max_tokens", 1800);
            JsonObject format = new JsonObject(); format.addProperty("type", "json_object"); body.add("response_format", format);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(AtlasMod.CONFIG.aiTimeoutSeconds))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
            PENDING.add(owner);
            long requestSession = session;
            MinecraftServer server = player.getServer();
            AtlasManager.say(player, "Слушаю. Дай мне немного подумать.");
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
                if (server.isStopped()) return;
                server.execute(() -> {
                    if (requestSession != session) return;
                    PENDING.remove(owner);
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(owner);
                    if (current == null || (!AtlasManager.active(current) && !AtlasShield.protects(current))) return;
                    if (error != null || response == null || response.statusCode() != 200) {
                        AtlasManager.say(current, "Не удалось связаться с AI.");
                        fallback(current, input);
                        AtlasMod.LOGGER.warn("Atlas AI request failed ({})", response == null ? "network/timeout" : response.statusCode()); return;
                    }
                    try {
                        if (response.body().length() > 131072) throw new IllegalArgumentException("Response too large");
                        String raw = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("choices")
                                .get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                        JsonObject result = JsonParser.parseString(raw).getAsJsonObject();
                        String reply = result.has("reply") ? result.get("reply").getAsString() : "Хорошо.";
                        AtlasManager.say(current, reply.substring(0, Math.min(reply.length(), 1000)));
                        JsonArray actions = result.has("actions") ? result.getAsJsonArray("actions") : new JsonArray();
                        if (actions.size() > 3) throw new IllegalArgumentException("Too many actions");
                        for (var element : actions) execute(current, element.getAsJsonObject());
                        history.addLast(user); history.addLast(message("assistant", raw));
                        while (history.size() > 12) history.removeFirst();
                    } catch (Exception invalid) {
                        AtlasManager.say(current, "AI прислал действие, которое я не могу выполнить. Попробуй уточнить просьбу.");
                        AtlasMod.LOGGER.warn("Atlas AI response rejected: {}", invalid.getClass().getSimpleName());
                    }
                });
            });
        } catch (Exception invalid) {
            PENDING.remove(owner);
            AtlasManager.say(player, "Проверь настройки AI endpoint/model на сервере. Встроенные просьбы работают.");
            fallback(player, input);
        }
    }
    private static boolean fallback(ServerPlayerEntity player, String input) {
        IntentParser.Intent intent = IntentParser.parse(input);
        if (intent.type().equals("unknown")) return false;
        AtlasManager.say(player, "Использую встроенный разбор просьбы.");
        AtlasManager.execute(player, intent);
        return true;
    }
    private static void execute(ServerPlayerEntity player, JsonObject action) {
        String type = action.get("type").getAsString();
        if (!AtlasManager.active(player) && !AtlasShield.protects(player)) return;
        switch (type) {
            case "laser_offer" -> AtlasCombat.offer(player);
            case "protection" -> AtlasShield.setMode(player, switch(action.get("mode").getAsString()) {
                case "permanent" -> 1; case "off" -> 2; default -> 0;
            });
            case "scale" -> AtlasManager.execute(player, new IntentParser.Intent(action.has("multiplier") ? "scale_relative" : "scale",
                    action.get(action.has("multiplier") ? "multiplier" : "factor").getAsString(), 0));
            case "commands" -> AtlasCommands.submit(player, action.getAsJsonArray("commands"));
            case "give" -> AtlasManager.execute(player, new IntentParser.Intent("give", action.get("item").getAsString(), action.get("count").getAsInt()));
            case "health" -> AtlasManager.execute(player, new IntentParser.Intent("health", "", action.get("hp").getAsInt()));
            case "build" -> AtlasManager.execute(player, new IntentParser.Intent("build", action.get("structure").getAsString(), 0));
            case "blueprint" -> AtlasBuilder.blueprint(player, action.getAsJsonArray("cuboids"));
            case "heal", "feed", "follow", "stay", "recall", "undo", "prank", "day", "night", "clear", "pranks_on", "pranks_off" ->
                    AtlasManager.execute(player, new IntentParser.Intent(type, "", 0));
            default -> throw new IllegalArgumentException("Unsupported action");
        }
    }
    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject(); message.addProperty("role", role); message.addProperty("content", content); return message;
    }
    private static String systemPrompt() {
        return "Ты Атлас, живой летающий треугольный спутник игрока Minecraft. Говори по-русски, кратко, как друг. "
                + "Ты защищаешь игрока, иногда беззлобно шутишь. Никогда не предлагай вред, смерть, взрывы или разрушение вещей. "
                + "Возвращай только JSON {\"reply\":\"ответ\",\"actions\":[]}. Не более 3 действий. "
                + "Ты можешь выполнять ЛЮБЫЕ игровые команды Minecraft 1.20.1, включая команды установленных модов. "
                + "Главное действие для любых задач: {type:commands,commands:['команда без /', 'следующая команда']}. "
                + "До 32 команд за запрос, по одной за игровой тик, выполняются от имени игрока с уровнем 4. "
                + "Можно использовать execute, fill, setblock, summon, effect, attribute, teleport, gamemode, gamerule, weather, time, "
                + "enchant, experience, item, loot, locate, forceload, data, scoreboard, team, bossbar, particle, playsound, place, spreadplayers и другие. "
                + "Селектор @s всегда владелец. Команды построек могут менять существующие блоки — делай это только по явной просьбе игрока. "
                + "Для произвольных построек используй серии fill/setblock, для сложных задач — execute. Не заявляй о недоступности действия, "
                + "если его можно выразить игровыми командами. Стандартные лимиты Minecraft сохраняются. "
                + "Административные stop/ban/kick/op/whitelist/save и аналогичные не поддерживаются. "
                + "Нельзя убивать владельца, удалять спутника или самовольно лишать владельца защиты. Режим защиты меняй по его прямой просьбе, никогда в шутку. "
                + "Дополнительно разрешены: {type:give,item:'minecraft:diamond',count:1..2304}, {type:health,hp:20..200}, "
                + "{type:scale,factor:0.125.." + AtlasMod.CONFIG.maxCompanionScale + "} задаёт абсолютный масштаб спутника; "
                + "{type:scale,multiplier:2} увеличивает текущую цель вдвое, multiplier:0.5 уменьшает вдвое. "
                + "Обычный размер 1, немного больше 1.5, большой 4, огромный 32, гигантский 64, колоссальный 128. "
                + "Рост плавный; большие размеры автоматически поднимаются в небо. Меняй только размер самого Атласа по просьбе игрока. "
                + "Для лазерной атаки только {type:laser_offer}: мод спросит подтверждение игрока. Не заменяй лазер командами kill/damage; "
                + "не подтверждай атаку за игрока. Обнаружение врагов само по себе не разрешает нападение. "
                + "Защита игрока: {type:protection,mode:'held'|'permanent'|'off'}. held — пока предмет в руках, permanent — всегда, off — отключить. "
                + "{type:build,structure:'house'|'tower'|'platform'}, {type:heal|feed|follow|stay|recall|undo|prank|day|night|clear|pranks_on|pranks_off}. "
                + "Для произвольной постройки {type:blueprint,cuboids:[{x1:0,y1:0,z1:0,x2:2,y2:0,z2:2,block:'minecraft:stone'}]}. "
                + "Координаты относительны к центру площадки перед игроком: x,z -24..24, y 0..32. "
                + "Не более 64 прямоугольных частей; суммарный объём не более " + AtlasMod.CONFIG.maxBuildBlocks + ". "
                + "Не заполняй помещения: пол, отдельные стены с дверным проёмом, крыша. Воздух указывать нельзя. "
                + "Материалы: " + AtlasBuilder.palette() + ". Постройки размещаются только в воздухе и растительности, твёрдые блоки сохраняются. "
                + "Если просьба не поддерживается, объясни это, actions:[]. Административные команды сервера недоступны. "
                + "Не заявляй, что действие выполнено: фактический результат сообщит мод. Поля и строки в ответе — строгий JSON с двойными кавычками.";
    }
    public static void clear() { session++; PENDING.clear(); MEMORY.clear(); }
}
