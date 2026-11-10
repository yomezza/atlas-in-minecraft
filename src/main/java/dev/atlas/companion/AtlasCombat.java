package dev.atlas.companion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Hostile detection can only offer an attack. A player must confirm the offered target list. */
public final class AtlasCombat {
    public static final double RANGE = 24;
    private static final RegistryKey<DamageType> ENERGY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, AtlasMod.id("energy"));
    public record Offer(UUID token, List<UUID> targets, long expiresAt) {}
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private AtlasCombat() {}
    public static boolean enemy(Entity entity, ServerPlayerEntity owner) {
        if (!(entity instanceof MobEntity mob) || !mob.isAlive()) return false;
        if (mob instanceof TameableEntity pet && owner.getUuid().equals(pet.getOwnerUuid())) return false;
        return mob.getType().getSpawnGroup() == SpawnGroup.MONSTER || mob.getTarget() == owner;
    }
    public static List<MobEntity> enemies(ServerPlayerEntity owner) {
        return owner.getServerWorld().getEntitiesByClass(MobEntity.class, owner.getBoundingBox().expand(RANGE),
                mob -> enemy(mob, owner) && mob.squaredDistanceTo(owner) <= RANGE * RANGE && owner.canSee(mob));
    }
    public static Offer pending(ServerPlayerEntity player) {
        Offer offer = OFFERS.get(player.getUuid());
        if (offer != null && offer.expiresAt < player.getServer().getTicks()) { OFFERS.remove(player.getUuid()); return null; }
        return offer;
    }
    public static void offer(ServerPlayerEntity player) {
        if (!AtlasManager.allowed(player)) return;
        if (AtlasManager.find(player) == null) { AtlasManager.say(player, "Для лазера сначала выпусти меня ПКМ."); return; }
        List<UUID> targets = enemies(player).stream().map(Entity::getUuid).toList();
        if (targets.isEmpty()) { AtlasManager.say(player, "Видимых врагов рядом нет. Луч пока не нужен."); return; }
        Offer offer = new Offer(UUID.randomUUID(), targets, player.getServer().getTicks() + 1200L);
        OFFERS.put(player.getUuid(), offer);
        Text yes = Text.literal(" [Да, лазером]").formatted(Formatting.RED).styled(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas laser confirm " + offer.token)));
        Text no = Text.literal(" [Нет]").formatted(Formatting.GRAY).styled(style -> style.withClickEvent(
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/atlas laser cancel " + offer.token)));
        AtlasManager.say(player, Text.literal("Зафиксировал врагов: " + targets.size() + ". Выжечь их лазером?").append(yes).append(no));
    }
    public static boolean confirm(ServerPlayerEntity player, String token) {
        Offer offer = pending(player);
        if (offer == null || !offer.token.toString().equals(token) || !AtlasManager.allowed(player)) return false;
        AtlasEntity atlas = AtlasManager.find(player);
        if (atlas == null) { AtlasManager.say(player, "Сначала выпусти меня, затем повтори приказ."); return false; }
        OFFERS.remove(player.getUuid());
        atlas.startAttack(offer.targets);
        AtlasManager.say(player, "Подтверждение принято. Луч включён. Новые цели потребуют нового разрешения.");
        return true;
    }
    public static void cancel(ServerPlayerEntity player) {
        OFFERS.remove(player.getUuid());
        AtlasEntity atlas = AtlasManager.find(player);
        if (atlas != null) atlas.stopAttack();
        AtlasManager.say(player, "Оружие выключено.");
    }
    public static boolean handleReply(ServerPlayerEntity player, String input) {
        String s = input.strip().toLowerCase(java.util.Locale.ROOT);
        Offer current = pending(player);
        if (current != null && s.matches("да[.!]?")) { confirm(player, current.token.toString()); return true; }
        if (current != null && s.matches("нет[.!]?")) { cancel(player); return true; }
        if (s.startsWith("laser confirm ")) {
            if (!confirm(player, s.substring(14).strip())) AtlasManager.say(player, "Это подтверждение уже истекло или не относится к твоим целям.");
            return true;
        }
        if (s.startsWith("laser cancel ")) {
            Offer offer = pending(player);
            if (offer != null && offer.token.toString().equals(s.substring(13).strip())) cancel(player);
            return true;
        }
        if (s.matches("(?:да[,! ]*)?(?:уничтожай|атакуй|жги|подтверждаю|включай лазер)[.!]?")) {
            Offer offer = pending(player);
            if (offer == null) AtlasManager.say(player, "Сначала выберем цели: «Атлас, найди врагов».");
            else confirm(player, offer.token.toString());
            return true;
        }
        if (s.matches("(?:нет[,! ]*)?(?:не атакуй|отмени атаку|отмена атаки|выключи лазер|стоп лазер)[.!]?")) { cancel(player); return true; }
        return false;
    }
    public static DamageSource energy(LivingEntity target, Entity source, ServerPlayerEntity owner) {
        return new DamageSource(target.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ENERGY), source, owner);
    }
    public static boolean strike(ServerPlayerEntity player, LivingEntity target) {
        if (!AtlasManager.allowed(player) || !enemy(target, player)) return false;
        target.timeUntilRegen = 0;
        return target.damage(energy(target, player, player), target.getMaxHealth() / 2.0f + target.getAbsorptionAmount());
    }
    public static void clear() { OFFERS.clear(); }
}
