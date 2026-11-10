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
        if (AtlasManager.find(owner) != null) {
            AtlasManager.say(owner, "Я уже рядом. Двух меня твоя самооценка не выдержит.");
            return TypedActionResult.fail(stack);
        }
        AtlasEntity atlas = new AtlasEntity(AtlasMod.ENTITY, world);
        atlas.setOwner(owner.getUuid());
        atlas.setPosition(player.getPos().add(player.getRotationVec(1).multiply(1.8)).add(0, 1, 0));
        if (world.spawnEntity(atlas)) {
            stack.decrement(1);
            AtlasManager.remember(owner, atlas);
            AtlasManager.say(owner, "Я рядом. Пиши «Атлас, помощь». За твою целостность теперь отвечаю я.");
        }
        return TypedActionResult.success(stack);
    }
    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.atlas_companion.atlas.hint").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.atlas_companion.atlas.chat").formatted(Formatting.DARK_RED));
    }
}
