package dev.atlas.companion;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

/** Local observations have no API cost and never execute actions. */
public final class AtlasObservations {
    private static final class Memory {
        int lastComment = -10000, nextBlock;
        String lastBlock = "";
        final LinkedHashSet<UUID> enemies = new LinkedHashSet<>();
    }
    private static final Map<UUID, Memory> MEMORIES = new HashMap<>();
    private AtlasObservations() {}
    public static void tick(ServerPlayerEntity player, int ticks) {
        if (!AtlasMod.CONFIG.ambientComments || ticks % 20 != 0) return;
        Memory memory = MEMORIES.computeIfAbsent(player.getUuid(), id -> { Memory m = new Memory(); m.nextBlock = ticks + 200; return m; });
        if (ticks - memory.lastComment < 200) return;
        var enemies = player.getServerWorld().getEntitiesByClass(HostileEntity.class, player.getBoundingBox().expand(12),
                enemy -> enemy.isAlive() && player.canSee(enemy) && !memory.enemies.contains(enemy.getUuid()));
        if (!enemies.isEmpty()) {
            HostileEntity enemy = enemies.stream().min(java.util.Comparator.comparingDouble(player::squaredDistanceTo)).orElseThrow();
            String[] endings = {". Похоже, кто-то забыл правила гостеприимства.", ". Я на страже. Ты только не проверяй его зубы руками.",
                    ". Когда создавал этот мир, я рассчитывал на более вежливых обитателей."};
            AtlasManager.say(player, Text.literal("Рядом ").append(enemy.getDisplayName()).append(endings[player.getRandom().nextInt(endings.length)]));
            memory.enemies.add(enemy.getUuid());
            if (memory.enemies.size() > 32) memory.enemies.remove(memory.enemies.iterator().next());
            AtlasEntity atlas = AtlasManager.find(player);
            if (atlas != null && !atlas.attacking() && AtlasCombat.pending(player) == null) AtlasCombat.offer(player);
            memory.lastComment = ticks; memory.nextBlock = ticks + AtlasMod.CONFIG.commentIntervalSeconds * 20; return;
        }
        if (ticks < memory.nextBlock) return;
        memory.nextBlock = ticks + AtlasMod.CONFIG.commentIntervalSeconds * 20;
        BlockPos target = null;
        HitResult hit = player.raycast(8, 1, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) target = block.getBlockPos();
        if (target == null || sameBlock(player, target, memory)) {
            for (BlockPos pos : BlockPos.iterate(player.getBlockPos().add(-4, -2, -4), player.getBlockPos().add(4, 3, 4))) {
                Block block = player.getWorld().getBlockState(pos).getBlock();
                if (interesting(block) && !sameBlock(player, pos, memory)) { target = pos.toImmutable(); break; }
            }
        }
        if (target == null || sameBlock(player, target, memory)) return;
        Block block = player.getWorld().getBlockState(target).getBlock();
        if (block == Blocks.AIR) return;
        String comment = block == Blocks.BEDROCK ? "Фундамент мира. Я сам его укладывал. Теперь у тебя есть инструмент, который умеет с ним спорить."
                : block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ? "Алмазы. Не забудь включить Удачу X — я спрятал здесь достаточно подарков."
                : block == Blocks.ANCIENT_DEBRIS ? "Древние обломки. Почти ровесники моих первых экспериментов."
                : block == Blocks.LAVA ? "Лава рядом. Красивая текстура, сомнительный маршрут."
                : block == Blocks.OBSIDIAN ? "Обсидиан. Немного терпения — или просто используй меня как инструмент."
                : block == Blocks.CHEST ? "Сундук. Надеюсь, внутри порядок хотя бы лучше, чем в твоём инвентаре."
                : block == Blocks.CRAFTING_TABLE ? "Верстак. Люблю смотреть, как ты собираешь новое из моих старых правил."
                : null;
        if (comment == null) AtlasManager.say(player, Text.literal("Вижу ").append(block.getName()).append(". Посмотрим, какую историю ты из этого построишь."));
        else AtlasManager.say(player, comment);
        memory.lastBlock = Registries.BLOCK.getId(block).toString(); memory.lastComment = ticks;
    }
    private static boolean sameBlock(ServerPlayerEntity player, BlockPos pos, Memory memory) {
        return Registries.BLOCK.getId(player.getWorld().getBlockState(pos).getBlock()).toString().equals(memory.lastBlock);
    }
    private static boolean interesting(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.BEDROCK || block == Blocks.LAVA || block == Blocks.OBSIDIAN || block == Blocks.CHEST || block == Blocks.CRAFTING_TABLE;
    }
    public static void clear() { MEMORIES.clear(); }
}
