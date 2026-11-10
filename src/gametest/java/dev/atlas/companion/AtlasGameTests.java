package dev.atlas.companion;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class AtlasGameTests implements FabricGameTest {
    private static ServerPlayerEntity player(TestContext context) {
        AtlasMod.CONFIG.allowNonOperators = true;
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.setPosition(context.getAbsolutePos(new BlockPos(3, 2, 3)).toCenterPos());
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(AtlasMod.ATLAS));
        return player;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deployedCompanionKeepsOwnerInNbt(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        context.assertTrue(atlas != null, "Companion must be deployed");
        context.assertTrue(player.getMainHandStack().isEmpty(), "Deployment consumes the item even in creative");
        NbtCompound nbt = new NbtCompound(); atlas.writeNbt(nbt);
        context.assertTrue(nbt.getUuid("Owner").equals(player.getUuid()), "Owner must persist in NBT");
        AtlasEntity restored = new AtlasEntity(AtlasMod.ENTITY, context.getWorld()); restored.readNbt(nbt);
        context.assertTrue(restored.ownerId().equals(player.getUuid()), "Owner must restore from NBT");
        AtlasManager.pickUp(player, atlas);
        context.assertTrue(atlas.isRemoved(), "Pickup removes the world entity");
        context.assertTrue(player.getInventory().contains(new ItemStack(AtlasMod.ATLAS)), "Pickup restores item");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownerSurvivesDamageAndKill(TestContext context) {
        ServerPlayerEntity player = player(context);
        float health = player.getHealth();
        player.damage(context.getWorld().getDamageSources().genericKill(), 1000);
        player.kill();
        context.assertTrue(player.isAlive() && player.getHealth() == health, "Active Atlas must prevent damage and /kill");
        AtlasManager.execute(player, new IntentParser.Intent("health", "", 40));
        context.assertTrue(player.getMaxHealth() == 40, "Health request must produce 40 HP");
        AtlasManager.execute(player, new IntentParser.Intent("health", "", 60));
        context.assertTrue(player.getMaxHealth() == 60, "Repeated requests must replace the bonus");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullGameCommandAndAdminBoundary(TestContext context) {
        ServerPlayerEntity player = player(context);
        // Direct dispatcher execution exercises the same source used by the command queue.
        try {
            int result = player.getServer().getCommandManager().getDispatcher().execute(
                    "effect give @s minecraft:night_vision 60 0 true", player.getCommandSource().withLevel(4));
            context.assertTrue(result == 1, "Arbitrary game commands must run as owner");
        } catch (Exception e) { throw new RuntimeException(e); }
        boolean rejected = false;
        try { AtlasCommands.direct(player, "execute as @s run stop"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected, "Nested server administration commands must be rejected");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "building", tickLimit = 100)
    public void constructionPreservesBlocksAndUndoWorks(TestContext context) {
        ServerPlayerEntity player = player(context);
        BlockPos base = player.getBlockPos().offset(player.getHorizontalFacing(), 10).add(-5, -1, -5);
        BlockPos marker = base.add(2, 0, 2);
        context.getWorld().setBlockState(marker, Blocks.QUARTZ_BLOCK.getDefaultState());
        AtlasBuilder.preset(player, "platform");
        context.runAtTick(8, () -> {
            context.assertTrue(context.getWorld().getBlockState(base).isOf(Blocks.STONE_BRICKS), "Platform must be built");
            context.assertTrue(context.getWorld().getBlockState(marker).isOf(Blocks.QUARTZ_BLOCK), "Existing blocks must survive");
            AtlasBuilder.undo(player);
        });
        context.runAtTick(16, () -> {
            context.assertTrue(context.getWorld().getBlockState(base).isAir(), "Undo restores original air");
            context.assertTrue(context.getWorld().getBlockState(marker).isOf(Blocks.QUARTZ_BLOCK), "Undo preserves pre-existing blocks");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "ai", tickLimit = 200)
    public void aiResponseExecutesEnchantedItemCommand(TestContext context) throws Exception {
        ServerPlayerEntity player = player(context);
        String oldEndpoint = AtlasMod.CONFIG.aiEndpoint;
        String oldVariable = AtlasMod.CONFIG.aiKeyEnvironmentVariable;
        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> received = new AtomicReference<>();
        mock.createContext("/v1/chat/completions", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonObject action = new JsonObject(); action.addProperty("type", "commands");
            JsonArray commands = new JsonArray(); commands.add("give @s minecraft:diamond_sword{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]} 1");
            action.add("commands", commands);
            JsonArray actions = new JsonArray(); actions.add(action);
            JsonObject result = new JsonObject(); result.addProperty("reply", "Зачарованный меч? Сейчас."); result.add("actions", actions);
            JsonObject message = new JsonObject(); message.addProperty("content", result.toString());
            JsonObject choice = new JsonObject(); choice.add("message", message);
            JsonArray choices = new JsonArray(); choices.add(choice);
            JsonObject response = new JsonObject(); response.add("choices", choices);
            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        mock.start();
        AtlasMod.CONFIG.aiEndpoint = "http://127.0.0.1:" + mock.getAddress().getPort() + "/v1/chat/completions";
        AtlasMod.CONFIG.aiKeyEnvironmentVariable = "ATLAS_TEST_KEY";
        AtlasAi.ask(player, "Дай зачарованный меч");
        context.addInstantFinalTask(() -> {
            boolean found = false;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isOf(net.minecraft.item.Items.DIAMOND_SWORD)
                        && EnchantmentHelper.getLevel(Enchantments.SHARPNESS, stack) == 5) found = true;
            }
            context.assertTrue(found, "AI commands must give a sword with the requested enchantment");
            context.assertTrue(received.get() != null && JsonParser.parseString(received.get()).getAsJsonObject()
                    .getAsJsonArray("messages").toString().contains("зачарованный"), "The full request must reach the provider");
            mock.stop(0); AtlasMod.CONFIG.aiEndpoint = oldEndpoint; AtlasMod.CONFIG.aiKeyEnvironmentVariable = oldVariable;
        });
        context.waitAndRun(180, () -> {
            mock.stop(0); AtlasMod.CONFIG.aiEndpoint = oldEndpoint; AtlasMod.CONFIG.aiKeyEnvironmentVariable = oldVariable;
        });
    }
}
