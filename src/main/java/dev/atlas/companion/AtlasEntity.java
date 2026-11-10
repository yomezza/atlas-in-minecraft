package dev.atlas.companion;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class AtlasEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private boolean staying;
    private Vec3d interpolationTarget;
    private int interpolationSteps;

    public AtlasEntity(EntityType<? extends AtlasEntity> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
        setInvulnerable(true);
    }
    @Override protected void initDataTracker() { dataTracker.startTracking(OWNER, Optional.empty()); }
    public UUID ownerId() { return dataTracker.get(OWNER).orElse(null); }
    public void setOwner(UUID uuid) { dataTracker.set(OWNER, Optional.ofNullable(uuid)); }
    public void setStaying(boolean value) { staying = value; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwner(nbt.getUuid("Owner"));
        staying = nbt.getBoolean("Staying");
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId() != null) nbt.putUuid("Owner", ownerId());
        nbt.putBoolean("Staying", staying);
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (interpolationSteps > 0 && interpolationTarget != null) {
                setPosition(getPos().lerp(interpolationTarget, 1.0 / interpolationSteps));
                interpolationSteps--;
            }
            return;
        }
        if (!(getWorld() instanceof ServerWorld world) || ownerId() == null) return;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId());
        if (owner == null || owner.isRemoved()) return;
        AtlasManager.remember(owner, this);
        if (owner.getWorld() != world) {
            AtlasEntity replacement = new AtlasEntity(AtlasMod.ENTITY, owner.getWorld());
            replacement.setOwner(ownerId());
            replacement.setPosition(owner.getPos().add(1.5, 1.05, 0));
            if (owner.getWorld().spawnEntity(replacement)) {
                discard();
                AtlasManager.remember(owner, replacement);
            }
            return;
        }
        if (!staying) {
            double angle = Math.toRadians(owner.getYaw() + 80);
            Vec3d target = owner.getPos().add(Math.cos(angle) * 1.7,
                    1.05 + Math.sin(age * 0.065) * 0.12, Math.sin(angle) * 1.7);
            double distance = getPos().squaredDistanceTo(target);
            setPosition(distance > 256 ? target : getPos().lerp(target, 0.18));
        }
    }
    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        interpolationTarget = new Vec3d(x, y, z);
        interpolationSteps = Math.max(1, steps);
    }
    @Override public boolean canHit() { return true; }
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
