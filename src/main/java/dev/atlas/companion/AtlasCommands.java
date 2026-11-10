package dev.atlas.companion;

import com.google.gson.JsonArray;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** The full Minecraft command dispatcher is available, including commands added by other mods. */
public final class AtlasCommands {
    private record Task(UUID owner, String command) {}
    private static final Deque<Task> QUEUE = new ArrayDeque<>();
    private static final Set<String> ADMIN = Set.of("stop", "kick", "ban", "ban-ip", "banlist", "pardon", "pardon-ip",
            "op", "deop", "whitelist", "save-all", "save-off", "save-on", "publish", "jfr", "debug");
    private AtlasCommands() {}
    public static void submit(ServerPlayerEntity player, JsonArray commands) {
        if (!AtlasManager.active(player)) return;
        if (commands.size() == 0 || commands.size() > 32) throw new IllegalArgumentException("За один запрос: 1–32 команды.");
        if (QUEUE.size() + commands.size() > 256) throw new IllegalArgumentException("Очередь занята. Попробуй чуть позже.");
        Deque<Task> validated = new ArrayDeque<>();
        for (var value : commands) {
            String command = value.getAsString().strip();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank() || command.length() > 8192 || command.contains("\n") || command.contains("\r"))
                throw new IllegalArgumentException("Неверная команда.");
            // Inspect every redirected command (execute ... run ..., return run ...), not only its root.
            ServerCommandSource source = player.getCommandSource().withLevel(4);
            var parsed = player.getServer().getCommandManager().getDispatcher().parse(command, source);
            var context = parsed.getContext();
            while (context != null) {
                if (!context.getNodes().isEmpty() && ADMIN.contains(context.getNodes().get(0).getNode().getName()))
                    throw new IllegalArgumentException("Атлас управляет игрой; административные команды сервера выполняет администратор.");
                context = context.getChild();
            }
            validated.addLast(new Task(player.getUuid(), command));
        }
        QUEUE.addAll(validated);
        AtlasManager.say(player, "Принял игровых команд: " + commands.size() + ".");
    }
    public static void direct(ServerPlayerEntity player, String command) {
        JsonArray array = new JsonArray(); array.add(command); submit(player, array);
    }
    public static void tick(MinecraftServer server) {
        // One command per tick keeps multi-step requests ordered and lets world updates run in between.
        Task task = QUEUE.pollFirst();
        if (task == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.owner);
        if (player == null || !AtlasManager.active(player)) return;
        try {
            int result = server.getCommandManager().getDispatcher().execute(task.command, player.getCommandSource().withLevel(4));
            AtlasManager.say(player, "Команда /" + task.command.split("\\s+", 2)[0] + " завершена, результат: " + result + ".");
        } catch (Exception invalid) {
            AtlasManager.say(player, "Команда не выполнена: " + invalid.getMessage());
        }
    }
    public static void clear() { QUEUE.clear(); }
}
