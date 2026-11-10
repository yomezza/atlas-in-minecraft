package dev.atlas.companion;

import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.math.ChunkPos;

public final class AtlasIdentity {
    public static final String ID = "CompanionId";
    public static final String OWNER = "Owner";
    static final ChunkTicketType<UUID> TICKET = ChunkTicketType.create("atlas_companion", java.util.Comparator.comparing(UUID::toString), 100);
    private AtlasIdentity() {}
    public static UUID id(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().containsUuid(ID) ? stack.getNbt().getUuid(ID) : null;
    }
    public static UUID owner(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().containsUuid(OWNER) ? stack.getNbt().getUuid(OWNER) : null;
    }
    public static boolean belongsTo(ItemStack stack, ServerPlayerEntity player) {
        return stack.isOf(AtlasMod.ATLAS) && (owner(stack) == null || owner(stack).equals(player.getUuid()));
    }
    public static void stamp(ItemStack stack, UUID id, UUID owner) {
        stack.getOrCreateNbt().putUuid(ID, id); stack.getOrCreateNbt().putUuid(OWNER, owner);
    }
    public static UUID bind(ItemStack stack, ServerPlayerEntity player) {
        if (!belongsTo(stack, player)) throw new IllegalArgumentException("Этот Атлас привязан к другому игроку.");
        AtlasCompanionState state = AtlasCompanionState.get(player.getServer());
        var binding = state.binding(player.getUuid());
        UUID id = id(stack);
        if (binding != null) {
            if (id != null && !id.equals(binding.id())) throw new IllegalArgumentException("У тебя уже есть другой привязанный Атлас.");
            id = binding.id();
        } else {
            if (id == null) id = UUID.randomUUID();
            state.put(player.getUuid(), id, false, player.getWorld().getRegistryKey().getValue().toString(), player.getBlockPos());
        }
        stamp(stack, id, player.getUuid()); return id;
    }
    public static void deployed(ServerPlayerEntity player, AtlasEntity entity) {
        AtlasCompanionState.get(player.getServer()).put(player.getUuid(), entity.getUuid(), true,
                entity.getWorld().getRegistryKey().getValue().toString(), entity.getBlockPos());
        if (entity.getWorld() instanceof ServerWorld world)
            world.getChunkManager().addTicket(TICKET, new ChunkPos(entity.getBlockPos()), 2, entity.getUuid());
    }
    public static void stored(ServerPlayerEntity player, UUID id) {
        AtlasCompanionState.get(player.getServer()).put(player.getUuid(), id, false,
                player.getWorld().getRegistryKey().getValue().toString(), player.getBlockPos());
    }
    public static boolean isDeployed(ServerPlayerEntity player) {
        var binding = AtlasCompanionState.get(player.getServer()).binding(player.getUuid());
        return binding != null && binding.deployed();
    }
    public static boolean hasItem(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++)
            if (belongsTo(player.getInventory().getStack(slot), player)) return true;
        return false;
    }
    public static boolean returnItem(ServerPlayerEntity player, ItemStack stack) {
        UUID id = id(stack);
        int existing = -1;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack other = player.getInventory().getStack(slot);
            if (other.isOf(AtlasMod.ATLAS) && id.equals(id(other)) && player.getUuid().equals(owner(other))) {
                if (existing < 0) { existing = slot; player.getInventory().setStack(slot, stack.copy()); }
                else player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        // Creative-mode insertStack discards items when full and still returns true.
        return existing >= 0 || (player.getInventory().getEmptySlot() >= 0 && player.getInventory().insertStack(stack));
    }
}
