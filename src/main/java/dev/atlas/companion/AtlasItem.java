package dev.atlas.companion;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

public final class AtlasItem extends Item {
    public AtlasItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        ServerPlayerEntity owner = (ServerPlayerEntity) player;
        if (!AtlasManager.allowed(owner)) {
            AtlasManager.say(owner, "На сервере мои способности доступны операторам. Владелец сервера может изменить это в настройках.");
            return TypedActionResult.fail(stack);
        }
        if (!AtlasIdentity.belongsTo(stack, owner)) {
            AtlasManager.say(owner, "Этот Атлас привязан к другому игроку."); return TypedActionResult.fail(stack);
        }
        if (player.isSneaking()) {
            try { AtlasIdentity.bind(stack, owner); }
            catch (IllegalArgumentException bound) { AtlasManager.say(owner, bound.getMessage()); return TypedActionResult.fail(stack); }
            AtlasTool.setSilk(stack, !AtlasTool.silk(stack));
            AtlasManager.say(owner, AtlasTool.silk(stack) ? "Добыча: Шёлковое касание. Верну блоки в исходном виде." : "Добыча: Удача X. Посмотрим, насколько щедрым бывает мой мир.");
            return TypedActionResult.success(stack);
        }
        if (AtlasManager.find(owner) != null || AtlasIdentity.isDeployed(owner)) {
            AtlasManager.say(owner, "Я уже рядом. Двух меня твоя самооценка не выдержит.");
            return TypedActionResult.fail(stack);
        }
        AtlasEntity atlas = new AtlasEntity(AtlasMod.ENTITY, world);
        try { atlas.setUuid(AtlasIdentity.bind(stack, owner)); }
        catch (IllegalArgumentException bound) {
            AtlasManager.say(owner, bound.getMessage()); return TypedActionResult.fail(stack);
        }
        atlas.setOwner(owner.getUuid());
        AtlasTool.ensureEnchantments(stack);
        atlas.setCompanionStack(stack);
        atlas.setPosition(player.getPos().add(player.getRotationVec(1).multiply(1.8)).add(0, 1, 0));
        if (world.spawnEntity(atlas)) {
            stack.decrement(1);
            AtlasIdentity.deployed(owner, atlas);
            AtlasManager.remember(owner, atlas);
            AtlasManager.say(owner, "Я рядом. Пиши «Атлас, помощь». За твою целостность теперь отвечаю я.");
        }
        return TypedActionResult.success(stack);
    }
    @Override public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        return AtlasTool.MINING_SPEED;
    }
    @Override public boolean isSuitableFor(BlockState state) { return true; }
    @Override public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        return (AtlasIdentity.owner(player.getMainHandStack()) == null || AtlasIdentity.owner(player.getMainHandStack()).equals(player.getUuid()))
                && (!(player instanceof ServerPlayerEntity owner) || AtlasManager.allowed(owner));
    }
    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient) return;
        AtlasTool.ensureEnchantments(stack);
        if (selected && entity instanceof ServerPlayerEntity owner && AtlasManager.allowed(owner) && AtlasIdentity.belongsTo(stack, owner)) {
            try { AtlasIdentity.bind(stack, owner); } catch (IllegalArgumentException ignored) { }
        }
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.atlas_companion.atlas.hint").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.atlas_companion.atlas.chat").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable(AtlasTool.silk(stack) ? "item.atlas_companion.atlas.silk" : "item.atlas_companion.atlas.fortune").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.atlas_companion.atlas.toggle").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.atlas_companion.atlas.tool").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.atlas_companion.atlas.settings").formatted(Formatting.DARK_GRAY));
        if (AtlasIdentity.id(stack) != null)
            tooltip.add(Text.translatable("item.atlas_companion.atlas.identity", AtlasIdentity.id(stack).toString().substring(0, 8)).formatted(Formatting.DARK_GRAY));
    }
}
