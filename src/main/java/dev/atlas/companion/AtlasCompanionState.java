package dev.atlas.companion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** One persistent companion identity per owner, including unloaded deployed companions. */
public final class AtlasCompanionState extends PersistentState {
    public record Binding(UUID id, boolean deployed, String dimension, BlockPos pos) {}
    private final Map<UUID, Binding> bindings = new HashMap<>();
    public static AtlasCompanionState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(AtlasCompanionState::fromNbt,
                AtlasCompanionState::new, "atlas_companions");
    }
    public Binding binding(UUID owner) { return bindings.get(owner); }
    public void put(UUID owner, UUID id, boolean deployed, String dimension, BlockPos pos) {
        Binding next = new Binding(id, deployed, dimension, pos.toImmutable());
        if (!next.equals(bindings.put(owner, next))) markDirty();
    }
    public static AtlasCompanionState fromNbt(NbtCompound nbt) {
        AtlasCompanionState state = new AtlasCompanionState();
        NbtList list = nbt.getList("Companions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (entry.containsUuid("Owner") && entry.containsUuid("CompanionId"))
                state.bindings.put(entry.getUuid("Owner"), new Binding(entry.getUuid("CompanionId"),
                        entry.getBoolean("Deployed"), entry.getString("Dimension"), BlockPos.fromLong(entry.getLong("Pos"))));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        bindings.forEach((owner, binding) -> {
            NbtCompound entry = new NbtCompound(); entry.putUuid("Owner", owner); entry.putUuid("CompanionId", binding.id);
            entry.putBoolean("Deployed", binding.deployed); entry.putString("Dimension", binding.dimension);
            entry.putLong("Pos", binding.pos.asLong()); list.add(entry);
        });
        nbt.put("Companions", list); return nbt;
    }
}
