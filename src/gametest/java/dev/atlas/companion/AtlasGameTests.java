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

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "scale", tickLimit = 220)
    public void controlledGrowthRisesIntoSkyAndShrinksWithStableIdentity(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.CONFIG.growthDurationSeconds = 4;
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player); java.util.UUID id = atlas.getUuid();
        AtlasManager.execute(player, new IntentParser.Intent("scale", "32", 0));
        context.assertTrue(atlas.currentScale() == 1 && atlas.targetScale() == 32, "Growth must set a controlled target, not jump instantly");
        context.runAtTick(10, () -> context.assertTrue(atlas.currentScale() > 1 && atlas.currentScale() < 32, "Scale must interpolate smoothly"));
        context.runAtTick(100, () -> {
            context.assertTrue(atlas.currentScale() == 32 && atlas.getY() > player.getY() + 30, "A huge companion must rise into the sky");
            context.assertTrue(atlas.getVisibilityBoundingBox().getYLength() > 30 && atlas.shouldRender(20000), "Huge rendering bounds must remain visible");
            NbtCompound nbt = new NbtCompound(); atlas.writeNbt(nbt);
            AtlasEntity restored = new AtlasEntity(AtlasMod.ENTITY, context.getWorld()); restored.readNbt(nbt);
            context.assertTrue(restored.currentScale() == 32 && restored.targetScale() == 32 && restored.getUuid().equals(id), "Scale and identity must persist in NBT");
            context.assertTrue(atlas.companionStack().getNbt().getFloat(AtlasScale.ITEM_KEY) == 32, "Recall item must preserve target scale");
            atlas.setTargetScale(1);
        });
        context.runAtTick(205, () -> {
            context.assertTrue(atlas.currentScale() == 1 && atlas.getY() < player.getY() + 3, "Normal scale must descend near the owner");
            context.assertTrue(atlas.getUuid().equals(id), "Resizing must not replace the companion");
            AtlasManager.pickUp(player, atlas); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "controls")
    public void wheelControlsChangeIndividualCompanionAndProtectionPersists(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasControlNetworking.action(player, "scale", 8, "");
        AtlasControlNetworking.action(player, "silk", 0, "");
        context.assertTrue(player.getMainHandStack().getNbt().getFloat(AtlasScale.ITEM_KEY) == 8 && AtlasTool.silk(player.getMainHandStack()), "Wheel must control held-item scale and mining mode");
        AtlasShield.setMode(player, 1);
        ItemStack item = player.getMainHandStack(); player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        context.assertTrue(AtlasShield.protects(player), "Permanent shield must remain after removing the item from hand");
        var state = AtlasCompanionState.get(player.getServer());
        context.assertTrue(AtlasCompanionState.fromNbt(state.writeNbt(new NbtCompound())).binding(player.getUuid()).protectionMode() == 1, "Protection preference must persist in world data");
        AtlasShield.setMode(player, 0); context.assertTrue(!AtlasShield.protects(player), "Held-only protection must disappear with empty hands");
        player.setStackInHand(Hand.MAIN_HAND, item); context.assertTrue(AtlasShield.protects(player), "Holding the item must restore shield protection");
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        context.assertTrue(!AtlasShield.protects(player), "Default protection must turn off when the item is deployed out of hand");
        AtlasControlNetworking.action(player, "follow", 0, "");
        context.assertTrue(atlas.flightMode() == 1 && atlas.silkMode(), "Wheel must control the deployed companion too");
        AtlasControlNetworking.action(player, "shield_permanent", 0, "");
        context.assertTrue(AtlasShield.protects(player), "Permanent protection must work with a deployed companion");
        AtlasManager.pickUp(player, atlas); AtlasShield.setMode(player, 0); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combat", tickLimit = 80)
    public void laserWaitsForConfirmationAndOnlyAttacksApprovedEnemies(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        var victim = context.spawnEntity(net.minecraft.entity.EntityType.CREEPER, new BlockPos(3, 2, 5)); victim.setAiDisabled(true);
        AtlasCombat.offer(player); var offer = AtlasCombat.pending(player);
        context.assertTrue(offer != null && !atlas.attacking(), "Detecting targets must only offer an attack");
        context.assertTrue(!AtlasCombat.confirm(player, java.util.UUID.randomUUID().toString()), "Invalid confirmation must not fire");
        java.util.concurrent.atomic.AtomicReference<net.minecraft.entity.mob.CreeperEntity> newcomer = new java.util.concurrent.atomic.AtomicReference<>();
        context.runAtTick(12, () -> {
            context.assertTrue(victim.getHealth() == victim.getMaxHealth() && atlas.laserTarget() == null, "No damage or beam is allowed before human confirmation");
            context.assertTrue(AtlasCombat.confirm(player, offer.token().toString()), "Owner can confirm the offered targets");
            var next = context.spawnEntity(net.minecraft.entity.EntityType.CREEPER, new BlockPos(5, 2, 5)); next.setAiDisabled(true); newcomer.set(next);
        });
        context.runAtTick(40, () -> {
            context.assertTrue(!victim.isAlive(), "Confirmed laser must cause real mob damage");
            context.assertTrue(newcomer.get().getHealth() == newcomer.get().getMaxHealth(), "Unapproved new enemies must not be attacked");
            context.assertTrue(AtlasCombat.pending(player) == null, "Confirmation must be consumed");
            AtlasCombat.cancel(player); AtlasManager.pickUp(player, atlas); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "combat")
    public void meleeDefeatsArmoredEnemiesInTwoHitsWithoutCommands(TestContext context) {
        ServerPlayerEntity player = player(context);
        var victim = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new BlockPos(3, 2, 5)); victim.setAiDisabled(true);
        victim.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.item.Items.NETHERITE_CHESTPLATE));
        context.assertTrue(AtlasCombat.strike(player, victim) && victim.isAlive() && victim.getHealth() == victim.getMaxHealth() / 2,
                "The first hit must remove half maximum health through armor");
        context.assertTrue(AtlasCombat.strike(player, victim) && !victim.isAlive(), "The second actual damage hit must defeat the enemy");
        context.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deployedCompanionKeepsOwnerInNbt(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.getMainHandStack().setCustomName(net.minecraft.text.Text.literal("Мой Атлас"));
        player.getMainHandStack().getOrCreateNbt().putString("PersonalMemory", "same companion");
        AtlasTool.setSilk(player.getMainHandStack(), true);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        context.assertTrue(atlas != null, "Companion must be deployed");
        context.assertTrue(player.getMainHandStack().isEmpty(), "Deployment consumes the item even in creative");
        NbtCompound nbt = new NbtCompound(); atlas.writeNbt(nbt);
        context.assertTrue(nbt.getUuid("Owner").equals(player.getUuid()), "Owner must persist in NBT");
        AtlasEntity restored = new AtlasEntity(AtlasMod.ENTITY, context.getWorld()); restored.readNbt(nbt);
        context.assertTrue(restored.ownerId().equals(player.getUuid()), "Owner must restore from NBT");
        AtlasManager.pickUp(player, atlas);
        AtlasManager.pickUp(player, atlas);
        context.assertTrue(atlas.isRemoved(), "Pickup removes the world entity");
        context.assertTrue(AtlasIdentity.hasItem(player), "Pickup restores the bound item");
        ItemStack returned = player.getMainHandStack();
        context.assertTrue(atlas.getUuid().equals(AtlasIdentity.id(returned)), "Item must preserve the companion UUID");
        context.assertTrue(player.getUuid().equals(AtlasIdentity.owner(returned)), "Item must preserve owner UUID");
        context.assertTrue(returned.getName().getString().equals("Мой Атлас")
                && returned.getNbt().getString("PersonalMemory").equals("same companion"), "Custom item data must survive the round trip");
        context.assertTrue(AtlasTool.silk(returned) && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, returned) == 1,
                "Mining mode and enchantments must survive recall");
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity again = AtlasManager.find(player);
        context.assertTrue(again != null && again.getUuid().equals(atlas.getUuid()), "Redeployment must restore the same companion identity");
        AtlasManager.pickUp(player, again);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "identity")
    public void copiedIdCannotDeployTwiceAndRecallReturnsOnlyOneItem(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        player.setStackInHand(Hand.MAIN_HAND, atlas.companionStack());
        var outcome = AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(outcome.getResult() == net.minecraft.util.ActionResult.FAIL, "Copied item must not deploy a second companion");
        context.assertTrue(AtlasManager.find(player) == atlas, "The original entity must remain active");
        AtlasManager.pickUp(player, atlas); AtlasManager.pickUp(player, atlas);
        int items = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++)
            if (atlas.getUuid().equals(AtlasIdentity.id(player.getInventory().getStack(slot)))) items++;
        context.assertTrue(items == 1, "Repeated recall and copied IDs must result in exactly one inventory item");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "identity")
    public void unloadedCompanionBindingPersistsAndBlocksAnotherDeployment(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        ItemStack copy = atlas.companionStack();
        var state = AtlasCompanionState.get(player.getServer());
        var restored = AtlasCompanionState.fromNbt(state.writeNbt(new NbtCompound())).binding(player.getUuid());
        context.assertTrue(restored.deployed() && restored.id().equals(atlas.getUuid()), "World data must preserve the deployed identity");
        atlas.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        player.setStackInHand(Hand.MAIN_HAND, copy);
        context.assertTrue(AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == net.minecraft.util.ActionResult.FAIL,
                "An unloaded companion must still block duplicate deployment");
        AtlasIdentity.stored(player, atlas.getUuid());
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "identity")
    public void foreignOwnerCannotUseBoundItem(TestContext context) {
        ServerPlayerEntity player = player(context);
        java.util.UUID owner = java.util.UUID.randomUUID();
        AtlasIdentity.stamp(player.getMainHandStack(), java.util.UUID.randomUUID(), owner);
        context.assertTrue(!AtlasIdentity.belongsTo(player.getMainHandStack(), player), "Borrowed item must not grant owner abilities");
        context.assertTrue(AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND).getResult() == net.minecraft.util.ActionResult.FAIL,
                "Another player must not deploy this companion");
        context.assertTrue(owner.equals(AtlasIdentity.owner(player.getMainHandStack())), "Failed deployment must preserve the original owner");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "identity")
    public void fullInventoryDoesNotLoseDeployedCompanion(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        for (int slot = 0; slot < player.getInventory().size(); slot++)
            player.getInventory().setStack(slot, new ItemStack(net.minecraft.item.Items.STONE, 64));
        AtlasManager.pickUp(player, atlas);
        context.assertTrue(!atlas.isRemoved() && AtlasIdentity.isDeployed(player), "Full inventory must preserve entity and deployed state");
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        AtlasManager.pickUp(player, atlas);
        context.assertTrue(atlas.isRemoved() && atlas.getUuid().equals(AtlasIdentity.id(player.getMainHandStack())), "Freeing a slot allows safe pickup");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tools")
    public void universalToolUsesRealFortuneAndSilkLoot(TestContext context) {
        ServerPlayerEntity player = player(context);
        ItemStack stack = player.getMainHandStack();
        AtlasTool.ensureEnchantments(stack);
        context.assertTrue(EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack) == 10, "Default mode must provide Fortune X");
        for (var block : java.util.List.of(Blocks.STONE, Blocks.OAK_LOG, Blocks.DIRT, Blocks.OBSIDIAN)) {
            context.assertTrue(AtlasMod.ATLAS.isSuitableFor(block.getDefaultState()), "Atlas must be suitable for every material");
            float speed = AtlasMod.ATLAS.getMiningSpeedMultiplier(stack, block.getDefaultState());
            context.assertTrue(speed > new ItemStack(net.minecraft.item.Items.NETHERITE_PICKAXE).getMiningSpeedMultiplier(Blocks.STONE.getDefaultState()),
                    "Atlas must mine faster than a netherite pickaxe");
        }
        player.setSneaking(true);
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(AtlasTool.silk(stack) && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) == 1
                && EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack) == 0, "Sneak-use must switch to Silk Touch without incompatible Fortune");
        var drops = net.minecraft.block.Block.getDroppedStacks(Blocks.DIAMOND_ORE.getDefaultState(), context.getWorld(),
                player.getBlockPos(), null, player, stack);
        context.assertTrue(drops.size() == 1 && drops.get(0).isOf(net.minecraft.item.Items.DIAMOND_ORE), "Silk Touch must produce the actual ore block");
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(!AtlasTool.silk(stack) && EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack) == 10
                && EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) == 0, "Switching back must restore Fortune X");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "tools")
    public void companionMinesBedrockAndProducesAnItem(TestContext context) {
        ServerPlayerEntity player = player(context);
        BlockPos relative = new BlockPos(3, 1, 4);
        BlockPos pos = context.getAbsolutePos(relative);
        context.getWorld().setBlockState(pos, Blocks.BEDROCK.getDefaultState());
        var result = AtlasTool.attack(player, context.getWorld(), Hand.MAIN_HAND, pos, net.minecraft.util.math.Direction.UP);
        context.assertTrue(result == net.minecraft.util.ActionResult.SUCCESS, "Tool must handle unbreakable blocks");
        context.assertTrue(context.getWorld().getBlockState(pos).isAir(), "Bedrock must actually be removed on the server");
        context.expectItemAt(net.minecraft.item.Items.BEDROCK, relative, 2);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "flight", tickLimit = 250)
    public void autonomousFlightMovesNearStationaryOwnerAndStayStopsIt(TestContext context) {
        ServerPlayerEntity player = player(context);
        AtlasMod.CONFIG.autonomousFlight = true;
        AtlasMod.ATLAS.use(context.getWorld(), player, Hand.MAIN_HAND);
        AtlasEntity atlas = AtlasManager.find(player);
        java.util.concurrent.atomic.AtomicReference<net.minecraft.util.math.Vec3d> first = new java.util.concurrent.atomic.AtomicReference<>();
        context.runAtTick(30, () -> first.set(atlas.getPos()));
        context.runAtTick(210, () -> {
            context.assertTrue(atlas.getPos().squaredDistanceTo(first.get()) > 0.005, "Companion must move even while the owner stays still; age="
                    + atlas.age + ", removed=" + atlas.isRemoved() + ", from=" + first.get() + ", to=" + atlas.getPos());
            context.assertTrue(atlas.squaredDistanceTo(player) < 36, "Autonomous flight must remain near the owner");
            atlas.setStaying(true); first.set(atlas.getPos());
        });
        context.runAtTick(220, () -> {
            context.assertTrue(atlas.getPos().equals(first.get()), "Stay must suspend autonomous flight");
            AtlasManager.pickUp(player, atlas); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "observations")
    public void enemyCommentsAreAutomaticThrottledAndCanBeDisabled(TestContext context) {
        java.util.List<String> messages = new java.util.ArrayList<>();
        ServerPlayerEntity watcher = new ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AtlasObserverTest")) {
            @Override public void sendMessage(net.minecraft.text.Text message, boolean overlay) { messages.add(message.getString()); }
        };
        watcher.setPosition(context.getAbsolutePos(new BlockPos(3, 2, 3)).toCenterPos());
        context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, new BlockPos(3, 2, 5));
        boolean previous = AtlasMod.CONFIG.ambientComments;
        try {
            AtlasMod.CONFIG.ambientComments = true;
            AtlasObservations.tick(watcher, 1000);
            context.assertTrue(messages.size() == 1 && messages.get(0).contains("Рядом"), "A visible enemy must trigger a chat comment");
            AtlasObservations.tick(watcher, 1020);
            context.assertTrue(messages.size() == 1, "Repeated sightings must not spam the chat");
            AtlasMod.CONFIG.ambientComments = false;
            AtlasObservations.tick(watcher, 2000);
            context.assertTrue(messages.size() == 1, "Disabling comments must suppress automatic messages");
        } finally { AtlasMod.CONFIG.ambientComments = previous; }
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
        String oldPrompt = AtlasMod.CONFIG.aiSystemPrompt;
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
        String customPrompt = "Ты создал этот мир. Игрок — твой напарник. Отвечай в образе древнего Атласа.";
        AtlasMod.CONFIG.aiSystemPrompt = customPrompt;
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
            JsonArray sentMessages = JsonParser.parseString(received.get()).getAsJsonObject().getAsJsonArray("messages");
            context.assertTrue(sentMessages.get(1).getAsJsonObject().get("role").getAsString().equals("system")
                    && sentMessages.get(1).getAsJsonObject().get("content").getAsString().equals(customPrompt),
                    "The configured context must be sent as a system message");
            mock.stop(0); AtlasMod.CONFIG.aiEndpoint = oldEndpoint; AtlasMod.CONFIG.aiKeyEnvironmentVariable = oldVariable;
            AtlasMod.CONFIG.aiSystemPrompt = oldPrompt;
        });
        context.waitAndRun(180, () -> {
            mock.stop(0); AtlasMod.CONFIG.aiEndpoint = oldEndpoint; AtlasMod.CONFIG.aiKeyEnvironmentVariable = oldVariable;
            AtlasMod.CONFIG.aiSystemPrompt = oldPrompt;
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "config")
    public void serverSettingsRequireOperatorAndApplyAtomically(TestContext context) throws Exception {
        ServerPlayerEntity player = player(context);
        AtlasConfig original = AtlasMod.CONFIG;
        AtlasConfig changed = original.copy();
        changed.harmlessPranks = !original.harmlessPranks;
        changed.aiSystemPrompt = "Я Атлас, создатель мира. Сохраняю новый контекст прямо из меню.";
        var players = player.getServer().getPlayerManager();
        players.removeFromOperators(player.getGameProfile());
        try {
            long revision = AtlasConfigNetworking.revision();
            context.assertTrue(!AtlasConfigNetworking.update(player, revision, changed.toJson()).saved(),
                    "allowNonOperators must not grant permission to change server settings");
            context.assertTrue(AtlasMod.CONFIG == original, "Denied edits must not change live settings");
            players.getOpList().add(new net.minecraft.server.OperatorEntry(player.getGameProfile(), 2, false));
            context.assertTrue(AtlasConfigNetworking.canEdit(player), "Explicit level-2 operators must have settings access");
            context.assertTrue(!AtlasConfigNetworking.update(player, revision, "{invalid").saved(), "Malformed JSON must be rejected");
            context.assertTrue(AtlasConfigNetworking.update(player, revision, changed.toJson()).saved(), "Operators must be able to save");
            context.assertTrue(AtlasMod.CONFIG.aiSystemPrompt.equals(changed.aiSystemPrompt), "New context must apply immediately");
            AtlasConfig disk = AtlasConfig.fromJson(java.nio.file.Files.readString(AtlasConfig.path()));
            context.assertTrue(disk.aiSystemPrompt.equals(changed.aiSystemPrompt), "Context must persist to disk");
            context.assertTrue(!AtlasConfigNetworking.update(player, revision, original.toJson()).saved(), "Stale edits must be rejected");
        } finally {
            players.removeFromOperators(player.getGameProfile()); original.save(); AtlasMod.CONFIG = original;
        }
        context.complete();
    }
}
