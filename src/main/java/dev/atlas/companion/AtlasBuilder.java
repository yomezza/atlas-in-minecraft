package dev.atlas.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Bounded jobs run on the server thread. Existing solid blocks are preserved. */
public final class AtlasBuilder {
    private record Change(BlockPos pos, BlockState before, BlockState after) {}
    private record History(ServerWorld world, List<Change> changes) {}
    private static final class Job {
        final UUID owner;
        final ServerWorld world;
        final Deque<Map.Entry<BlockPos, BlockState>> blocks;
        final List<Change> changes = new ArrayList<>();
        final boolean undo;
        final Map<BlockPos, BlockState> expected;
        Job(UUID owner, ServerWorld world, Map<BlockPos, BlockState> plan, boolean undo, Map<BlockPos, BlockState> expected) {
            this.owner = owner; this.world = world; this.blocks = new ArrayDeque<>(plan.entrySet());
            this.undo = undo; this.expected = expected;
        }
    }
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Map<String, Block> PALETTE = Map.ofEntries(
            Map.entry("minecraft:oak_planks", Blocks.OAK_PLANKS), Map.entry("minecraft:oak_log", Blocks.OAK_LOG),
            Map.entry("minecraft:glass", Blocks.GLASS), Map.entry("minecraft:stone_bricks", Blocks.STONE_BRICKS),
            Map.entry("minecraft:cobblestone", Blocks.COBBLESTONE), Map.entry("minecraft:stone", Blocks.STONE),
            Map.entry("minecraft:quartz_block", Blocks.QUARTZ_BLOCK), Map.entry("minecraft:black_concrete", Blocks.BLACK_CONCRETE),
            Map.entry("minecraft:red_concrete", Blocks.RED_CONCRETE), Map.entry("minecraft:white_concrete", Blocks.WHITE_CONCRETE),
            Map.entry("minecraft:deepslate_bricks", Blocks.DEEPSLATE_BRICKS), Map.entry("minecraft:glowstone", Blocks.GLOWSTONE),
            Map.entry("minecraft:sea_lantern", Blocks.SEA_LANTERN), Map.entry("minecraft:bricks", Blocks.BRICKS),
            Map.entry("minecraft:spruce_planks", Blocks.SPRUCE_PLANKS), Map.entry("minecraft:obsidian", Blocks.OBSIDIAN));

    private AtlasBuilder() {}
    private static BlockPos origin(ServerPlayerEntity player, int size) {
        var direction = player.getHorizontalFacing();
        return player.getBlockPos().offset(direction, size / 2 + 5).add(-size / 2, -1, -size / 2);
    }
    public static void preset(ServerPlayerEntity player, String kind) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        int size = kind.equals("platform") ? 11 : 7;
        BlockPos base = origin(player, size);
        if (kind.equals("platform")) {
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++)
                blocks.put(base.add(x, 0, z), Blocks.STONE_BRICKS.getDefaultState());
        } else if (kind.equals("house")) {
            for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                blocks.put(base.add(x, 0, z), Blocks.OAK_PLANKS.getDefaultState());
                for (int y = 1; y <= 4; y++) {
                    boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                    boolean door = x == 3 && z == 0 && y <= 2;
                    boolean window = y == 2 && (x == 2 || x == 4 || z == 2 || z == 4);
                    if (edge && !door) blocks.put(base.add(x, y, z),
                            (window ? Blocks.GLASS : Blocks.OAK_PLANKS).getDefaultState());
                }
            }
            for (int layer = 0; layer < 4; layer++) for (int x = layer; x < 7 - layer; x++) for (int z = 0; z < 7; z++)
                blocks.put(base.add(x, 5 + layer, z), Blocks.SPRUCE_PLANKS.getDefaultState());
            blocks.put(base.add(3, 4, 3), Blocks.GLOWSTONE.getDefaultState());
        } else if (kind.equals("tower")) {
            for (int y = 0; y <= 12; y++) for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
                boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                boolean door = x == 3 && z == 0 && y > 0 && y <= 2;
                if ((edge || y == 0 || y == 12) && !door) blocks.put(base.add(x, y, z),
                        (y % 4 == 2 && (x == 3 || z == 3) ? Blocks.GLASS : Blocks.STONE_BRICKS).getDefaultState());
            }
            // A walkable internal staircase along the perimeter.
            for (int y = 1; y < 12; y++) {
                int step = y - 1;
                int x = step < 5 ? 1 + step : step < 9 ? 5 : 5 - (step - 8);
                int z = step < 5 ? 1 : step < 9 ? 1 + step - 4 : 5;
                blocks.put(base.add(x, y, z), Blocks.OAK_PLANKS.getDefaultState());
            }
            blocks.put(base.add(3, 11, 3), Blocks.GLOWSTONE.getDefaultState());
        } else { AtlasManager.say(player, "Встроенные постройки: дом, башня, платформа."); return; }
        start(player, blocks);
    }
    public static void blueprint(ServerPlayerEntity player, JsonArray cuboids) {
        if (cuboids.size() > 64) throw new IllegalArgumentException("Не более 64 частей в постройке.");
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockPos center = origin(player, 49).add(24, 0, 24);
        long total = 0;
        for (var value : cuboids) {
            JsonObject c = value.getAsJsonObject();
            int x1 = coord(c, "x1", -24, 24), x2 = coord(c, "x2", -24, 24);
            int y1 = coord(c, "y1", 0, 32), y2 = coord(c, "y2", 0, 32);
            int z1 = coord(c, "z1", -24, 24), z2 = coord(c, "z2", -24, 24);
            if (x2 < x1 || y2 < y1 || z2 < z1) throw new IllegalArgumentException("Неверные границы части постройки.");
            total += (long)(x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
            if (total > AtlasMod.CONFIG.maxBuildBlocks) throw new IllegalArgumentException("Постройка превышает лимит блоков.");
            String id = c.get("block").getAsString();
            Block block = PALETTE.get(id);
            if (block == null) throw new IllegalArgumentException("Материал не входит в палитру: " + id);
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
                blocks.put(center.add(x, y, z), block.getDefaultState());
        }
        start(player, blocks);
    }
    private static int coord(JsonObject object, String key, int min, int max) {
        int value = object.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException("Координата вне допустимых границ.");
        return value;
    }
    private static void start(ServerPlayerEntity player, Map<BlockPos, BlockState> blocks) {
        if (JOBS.containsKey(player.getUuid())) { AtlasManager.say(player, "Сначала закончу текущую работу."); return; }
        if (blocks.isEmpty() || blocks.size() > AtlasMod.CONFIG.maxBuildBlocks) {
            AtlasManager.say(player, "Постройка пустая или превышает лимит блоков."); return;
        }
        ServerWorld world = player.getServerWorld();
        for (BlockPos pos : blocks.keySet()) {
            if (!world.getWorldBorder().contains(pos) || pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()
                    || !world.isChunkLoaded(pos)) {
                AtlasManager.say(player, "Здесь постройка выходит за границы мира или загруженных чанков."); return;
            }
        }
        JOBS.put(player.getUuid(), new Job(player.getUuid(), world, blocks, false, Map.of()));
        AtlasManager.say(player, "Строю рядом. Существующие твёрдые блоки сохраню. Отмена: «Атлас, отмени постройку».");
    }
    public static void undo(ServerPlayerEntity player) {
        Job running = JOBS.get(player.getUuid());
        if (running != null && running.undo) { AtlasManager.say(player, "Уже отменяю."); return; }
        History history;
        if (running != null) { JOBS.remove(player.getUuid()); history = new History(running.world, running.changes); }
        else history = HISTORY.remove(player.getUuid());
        if (history == null || history.changes.isEmpty()) { AtlasManager.say(player, "Мне пока нечего отменять."); return; }
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>(), expected = new HashMap<>();
        for (int i = history.changes.size() - 1; i >= 0; i--) {
            Change c = history.changes.get(i); plan.put(c.pos, c.before); expected.put(c.pos, c.after);
        }
        JOBS.put(player.getUuid(), new Job(player.getUuid(), history.world, plan, true, expected));
        AtlasManager.say(player, "Убираю последнюю постройку.");
    }
    public static void tick(MinecraftServer server) {
        var iterator = JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Job job = iterator.next().getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(job.owner);
            if (player == null || !AtlasManager.allowed(player)) {
                if (!job.undo && !job.changes.isEmpty()) HISTORY.put(job.owner, new History(job.world, job.changes));
                iterator.remove(); continue;
            }
            for (int i = 0; i < 128 && !job.blocks.isEmpty(); i++) {
                var entry = job.blocks.removeFirst();
                BlockPos pos = entry.getKey();
                if (!job.world.isChunkLoaded(pos)) continue;
                BlockState current = job.world.getBlockState(pos);
                if (job.world.getBlockEntity(pos) != null) continue;
                if (job.undo) { if (!current.equals(job.expected.get(pos))) continue; }
                else if (!current.isReplaceable() || !current.getFluidState().isEmpty()) continue;
                if (!entry.getValue().isAir() && !job.world.getEntitiesByClass(LivingEntity.class, new Box(pos), e -> true).isEmpty()) continue;
                if (job.world.setBlockState(pos, entry.getValue(), Block.NOTIFY_ALL))
                    job.changes.add(new Change(pos, current, entry.getValue()));
            }
            if (job.blocks.isEmpty()) {
                if (!job.undo) HISTORY.put(job.owner, new History(job.world, job.changes));
                AtlasManager.say(player, (job.undo ? "Отмена завершена. Изменено блоков: " : "Готово. Поставлено блоков: ") + job.changes.size());
                iterator.remove();
            }
        }
    }
    public static String palette() { return String.join(", ", PALETTE.keySet()); }
    public static void clear() { JOBS.clear(); HISTORY.clear(); }
}
