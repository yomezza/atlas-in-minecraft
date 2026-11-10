package dev.atlas.companion;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class AtlasTool {
    public static final String SILK = "AtlasSilkTouch";
    public static final int FORTUNE_LEVEL = 10;
    public static final float MINING_SPEED = 18;
    private AtlasTool() {}
    public static boolean silk(ItemStack stack) { return stack.hasNbt() && stack.getNbt().getBoolean(SILK); }
    public static void setSilk(ItemStack stack, boolean silk) {
        stack.getOrCreateNbt().putBoolean(SILK, silk); ensureEnchantments(stack);
    }
    public static void ensureEnchantments(ItemStack stack) {
        boolean silk = silk(stack);
        if (EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack) == (silk ? 0 : FORTUNE_LEVEL)
                && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) == (silk ? 1 : 0)) return;
        var enchantments = EnchantmentHelper.get(stack);
        enchantments.remove(Enchantments.FORTUNE); enchantments.remove(Enchantments.SILK_TOUCH);
        enchantments.put(silk ? Enchantments.SILK_TOUCH : Enchantments.FORTUNE, silk ? 1 : FORTUNE_LEVEL);
        EnchantmentHelper.set(enchantments, stack);
    }
    public static void register() {
        AttackBlockCallback.EVENT.register(AtlasTool::attack);
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(AtlasMod.ATLAS) || hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            if (world.isClient) return ActionResult.PASS;
            ServerPlayerEntity owner = (ServerPlayerEntity)player;
            if (!AtlasIdentity.belongsTo(stack, owner) || !AtlasManager.allowed(owner)) return ActionResult.FAIL;
            if (!(target instanceof net.minecraft.entity.LivingEntity living) || !AtlasCombat.enemy(target, owner)) return ActionResult.PASS;
            if (owner.getAttackCooldownProgress(0.5f) < 0.9f) return ActionResult.FAIL;
            AtlasCombat.strike(owner, living); owner.resetLastAttackedTicks();
            owner.swingHand(hand, true);
            return ActionResult.SUCCESS;
        });
    }
    public static ActionResult attack(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(AtlasMod.ATLAS) || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player.isSpectator()) return ActionResult.FAIL;
        if (AtlasIdentity.owner(stack) != null && !AtlasIdentity.owner(stack).equals(player.getUuid())) return ActionResult.FAIL;
        BlockState state = world.getBlockState(pos);
        if (world.isClient) return state.getHardness(world, pos) < 0 ? ActionResult.SUCCESS : ActionResult.PASS;
        ServerPlayerEntity owner = (ServerPlayerEntity)player;
        if (!AtlasManager.allowed(owner)) return cancel(owner, pos);
        ensureEnchantments(stack);
        if (state.getHardness(world, pos) >= 0 || state.isAir()) return ActionResult.PASS;
        if (!world.canPlayerModifyAt(player, pos) || !player.canModifyBlocks() || !world.getWorldBorder().contains(pos)
                || player.squaredDistanceTo(pos.toCenterPos()) > 36) return cancel(owner, pos);
        var entity = world.getBlockEntity(pos);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state, entity)) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(world, player, pos, state, entity);
            return cancel(owner, pos);
        }
        if (world.breakBlock(pos, false, player)) {
            if (state.getBlock().asItem() != Items.AIR)
                Block.dropStack(world, pos, new ItemStack(state.getBlock().asItem()));
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(world, player, pos, state, entity);
        }
        return ActionResult.SUCCESS;
    }
    private static ActionResult cancel(ServerPlayerEntity player, BlockPos pos) {
        player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getWorld(), pos)); return ActionResult.FAIL;
    }
}
