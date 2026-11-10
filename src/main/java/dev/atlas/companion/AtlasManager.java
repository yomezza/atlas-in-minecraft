package dev.atlas.companion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class AtlasManager {
    private static final Map<UUID, UUID> DEPLOYED = new HashMap<>();
    private static final Map<UUID, Anchor> SAFE = new HashMap<>();
    private static final Map<UUID, Long> LAST_COMMAND = new HashMap<>();
    private static final Map<UUID, Boolean> PRANKS = new HashMap<>();
    private static final UUID HEALTH_MODIFIER = UUID.fromString("b49068bd-6728-436c-a9e8-94dfd71c5bcc");
    private record Anchor(ServerWorld world, BlockPos pos) {}
    private AtlasManager() {}
    public static boolean allowed(ServerPlayerEntity player) {
        return player.getServer() != null && (player.getServer().isSingleplayer()
                || player.hasPermissionLevel(2) || AtlasMod.CONFIG.allowNonOperators);
    }
    public static void remember(ServerPlayerEntity player, AtlasEntity entity) {
        UUID previous = DEPLOYED.get(player.getUuid());
        if (previous != null && !previous.equals(entity.getUuid())) {
            AtlasEntity other = find(player);
            if (other != null && other != entity) { entity.discard(); return; }
        }
        DEPLOYED.put(player.getUuid(), entity.getUuid());
    }
    public static AtlasEntity find(ServerPlayerEntity player) {
        UUID id = DEPLOYED.get(player.getUuid());
        if (id != null) for (ServerWorld world : player.getServer().getWorlds()) {
            if (world.getEntity(id) instanceof AtlasEntity atlas && !atlas.isRemoved()) return atlas;
        }
        // NBT-loaded entities re-register themselves on tick; recover immediately on login too.
        for (ServerWorld world : player.getServer().getWorlds()) for (var entity : world.iterateEntities()) {
            if (entity instanceof AtlasEntity atlas && !atlas.isRemoved() && player.getUuid().equals(atlas.ownerId())) {
                DEPLOYED.put(player.getUuid(), atlas.getUuid()); return atlas;
            }
        }
        DEPLOYED.remove(player.getUuid());
        return null;
    }
    public static boolean active(ServerPlayerEntity player) {
        return allowed(player) && (player.getMainHandStack().isOf(AtlasMod.ATLAS)
                || player.getOffHandStack().isOf(AtlasMod.ATLAS) || find(player) != null);
    }
    public static void say(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal("<Атлас> ").formatted(Formatting.RED)
                .append(Text.literal(message).formatted(Formatting.WHITE)), false);
    }
    public static void command(ServerPlayerEntity player, String message) {
        if (!allowed(player)) { say(player, "На этом сервере мои способности разрешены только операторам."); return; }
        IntentParser.Intent intent = IntentParser.parse(message);
        if (intent.type().equals("help")) { help(player); return; }
        if (intent.type().equals("summon")) { summon(player); return; }
        if (!active(player)) {
            say(player, "Возьми меня в руку или выпусти ПКМ. Получить предмет: /atlas призови"); return;
        }
        long now = System.currentTimeMillis();
        if (now - LAST_COMMAND.getOrDefault(player.getUuid(), 0L) < 650) return;
        LAST_COMMAND.put(player.getUuid(), now);
        String direct = message.strip();
        if (direct.startsWith("выполни ") || direct.startsWith("команда ") || direct.startsWith("run ")) {
            try { AtlasCommands.direct(player, direct.substring(direct.indexOf(' ') + 1)); }
            catch (Exception invalid) { say(player, invalid.getMessage()); }
            return;
        }
        // Let the model see the whole request: "give an enchanted sword" must not become a plain sword.
        if (AtlasMod.CONFIG.aiEnabled) AtlasAi.ask(player, message);
        else if (!intent.type().equals("unknown")) execute(player, intent);
        else say(player, "Без подключённой языковой модели я понимаю встроенные просьбы. Например: «дай 64 алмаза», «построй дом», «увеличь здоровье до 40», «пошути». Список: «Атлас, помощь».");
    }
    private static void help(ServerPlayerEntity player) {
        say(player, "ПКМ с предметом — выпустить; Shift+ПКМ по мне — забрать. Обращайся «Атлас, …» или /atlas …\n"
                + "Просьбы: призови; дай 64 алмаза; дай minecraft:oak_log 64; вылечи; накорми; увеличь здоровье до 40; "
                + "построй дом / башню / платформу; отмени постройку; следуй за мной; стой; вернись в руку; пошути; "
                + "не шути; можешь шутить; сделай день; сделай ночь; убери дождь.\n"
                + "Любая игровая команда: «Атлас, выполни summon minecraft:allay ~ ~1 ~». "
                + "Здоровье задаётся в HP: 40 = 20 сердец. Свободные разговоры и перевод просьб в команды требуют настройки AI.");
    }
    private static void summon(ServerPlayerEntity player) {
        if (find(player) != null || player.getInventory().contains(new ItemStack(AtlasMod.ATLAS))) {
            say(player, "Я уже у тебя. Проверь инвентарь или оглянись."); return;
        }
        ItemStack stack = new ItemStack(AtlasMod.ATLAS);
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        say(player, "Вот и я. Возьми предмет и нажми ПКМ.");
    }
    public static void pickUp(ServerPlayerEntity player, AtlasEntity atlas) {
        if (!player.getUuid().equals(atlas.ownerId())) { say(player, "У меня уже есть спутник-человек."); return; }
        ItemStack stack = new ItemStack(AtlasMod.ATLAS);
        if (!player.getInventory().insertStack(stack)) { say(player, "Освободи одно место в инвентаре."); return; }
        atlas.discard(); DEPLOYED.remove(player.getUuid());
        say(player, "Карманный режим. Я всё вижу.");
    }
    public static void execute(ServerPlayerEntity player, IntentParser.Intent intent) {
        if (!allowed(player) || !active(player)) return;
        switch (intent.type()) {
            case "give" -> give(player, intent.argument(), intent.amount());
            case "heal" -> { player.setHealth(player.getMaxHealth()); player.extinguish(); say(player, "Починил человека."); }
            case "feed" -> { player.getHungerManager().setFoodLevel(20); player.getHungerManager().setSaturationLevel(20); say(player, "Сытый человек — предсказуемый человек."); }
            case "health" -> {
                int health = Math.max(20, Math.min(200, intent.amount()));
                var attribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                if (attribute != null) {
                    attribute.removeModifier(HEALTH_MODIFIER);
                    attribute.addPersistentModifier(new EntityAttributeModifier(HEALTH_MODIFIER, "Atlas bonus health",
                            Math.max(0, health - attribute.getBaseValue()), EntityAttributeModifier.Operation.ADDITION));
                    player.setHealth(player.getMaxHealth());
                    say(player, "Теперь у тебя " + (int) player.getMaxHealth() + " HP. Всё равно не проверяй это лавой.");
                }
            }
            case "build" -> AtlasBuilder.preset(player, intent.argument());
            case "undo" -> AtlasBuilder.undo(player);
            case "follow", "stay", "recall" -> {
                AtlasEntity atlas = find(player);
                if (atlas == null) { say(player, "Сначала выпусти меня ПКМ."); break; }
                if (intent.type().equals("recall")) pickUp(player, atlas);
                else { atlas.setStaying(intent.type().equals("stay")); say(player, intent.type().equals("stay") ? "Жду здесь." : "Лечу за тобой."); }
            }
            case "prank" -> prank(player);
            case "pranks_off" -> { PRANKS.put(player.getUuid(), false); say(player, "Хорошо. Буду загадочно молчать."); }
            case "pranks_on" -> { PRANKS.put(player.getUuid(), true); say(player, "Ты сам это предложил."); }
            case "day" -> { player.getServerWorld().setTimeOfDay(1000); say(player, "Утро заказано."); }
            case "night" -> { player.getServerWorld().setTimeOfDay(13000); say(player, "Ночь. Я на страже."); }
            case "clear" -> { player.getServerWorld().setWeather(12000, 0, false, false); say(player, "Дождь отменён."); }
            default -> say(player, "Такого действия у меня пока нет. Напиши «помощь».");
        }
    }
    private static void give(ServerPlayerEntity player, String rawId, int requested) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null || !Registries.ITEM.containsId(id)) { say(player, "Не нашёл предмет " + rawId); return; }
        Item item = Registries.ITEM.get(id);
        if (item == net.minecraft.item.Items.AIR) { say(player, "Воздух у тебя уже есть."); return; }
        int count = Math.max(1, Math.min(2304, requested));
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(item, Math.min(remaining, item.getMaxCount()));
            remaining -= stack.getCount();
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        }
        say(player, "Держи: " + item.getName().getString() + " × " + count + ". Законы экономики отвернулись.");
    }
    private static void prank(ServerPlayerEntity player) {
        String[] lines = {"Я удалил твой дом. Шутка. Даже я знаю, сколько ты выбирал окна.",
                "Сканирование завершено: главный источник опасности — твои решения.",
                "За спиной крипер! Уже нет. Его и не было. Зато реакция отличная.",
                "Повысил твой интеллект на 200%. Изменений пока не обнаружено."};
        say(player, lines[player.getRandom().nextInt(lines.length)]);
        player.getServerWorld().spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2,
                player.getZ(), 8, 0.35, 0.25, 0.35, 0.01);
    }
    public static void tick(MinecraftServer server) {
        AtlasCommands.tick(server);
        AtlasBuilder.tick(server);
        if (server.getTicks() % 10 != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!active(player)) continue;
            if (AtlasMod.CONFIG.protectOwner && player.isAlive() && !player.isSpectator()) {
                if (player.isOnGround() && !player.isInLava() && !player.isOnFire())
                    SAFE.put(player.getUuid(), new Anchor(player.getServerWorld(), player.getBlockPos().toImmutable()));
                player.extinguish(); player.setAir(player.getMaxAir()); player.fallDistance = 0;
                if (player.getHealth() < player.getMaxHealth()) player.heal(2);
                if (player.getY() < player.getWorld().getBottomY() + 4 || player.isInLava()) rescue(player);
            }
            if (AtlasMod.CONFIG.harmlessPranks && PRANKS.getOrDefault(player.getUuid(), true)
                    && server.getTicks() % 2400 == 0 && player.getRandom().nextFloat() < 0.2f) prank(player);
        }
    }
    private static void rescue(ServerPlayerEntity player) {
        Anchor anchor = SAFE.get(player.getUuid());
        ServerWorld world = player.getServerWorld();
        BlockPos pos = anchor != null && anchor.world == world ? anchor.pos : world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, world.getSpawnPos());
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos);
        }
        player.teleport(world, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.setVelocity(0, 0, 0); player.fallDistance = 0;
        say(player, "Вернул в безопасное место. Давай без повторения эксперимента.");
    }
    public static void clear() {
        DEPLOYED.clear(); SAFE.clear(); LAST_COMMAND.clear(); PRANKS.clear(); AtlasBuilder.clear(); AtlasCommands.clear();
    }
}
