package dev.atlas.companion;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlasMod implements ModInitializer {
    public static final String ID = "atlas_companion";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    public static final Item ATLAS = new AtlasItem(new Item.Settings().maxCount(1).fireproof());
    public static final EntityType<AtlasEntity> ENTITY = Registry.register(Registries.ENTITY_TYPE,
            id("atlas"), net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder
                    .create(SpawnGroup.MISC, AtlasEntity::new).dimensions(EntityDimensions.fixed(0.65f, 0.8f))
                    .trackRangeBlocks(80).trackedUpdateRate(2).build());
    public static volatile AtlasConfig CONFIG;

    public static Identifier id(String name) { return new Identifier(ID, name); }

    @Override public void onInitialize() {
        CONFIG = AtlasConfig.load();
        AtlasConfigNetworking.register();
        AtlasTool.register();
        Registry.register(Registries.ITEM, id("atlas"), ATLAS);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ATLAS));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                CommandManager.literal("atlas").executes(ctx -> {
                    AtlasManager.command(ctx.getSource().getPlayerOrThrow(), "помощь"); return 1;
                }).then(CommandManager.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    AtlasManager.command(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "message")); return 1;
                }))));
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String addressed = IntentParser.addressedMessage(message.getContent().getString());
            if (addressed != null) AtlasManager.command(sender, addressed);
        });
        ServerTickEvents.END_SERVER_TICK.register(AtlasManager::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity player && CONFIG.protectOwner && AtlasManager.active(player)));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof AtlasEntity atlas) || !player.isSneaking()) return ActionResult.PASS;
            if (!world.isClient && player instanceof ServerPlayerEntity owner) AtlasManager.pickUp(owner, atlas);
            return ActionResult.SUCCESS;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { AtlasManager.clear(); AtlasAi.clear(); });
    }
}
