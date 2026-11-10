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
import net.minecraft.item.ItemStack;

public final class AtlasEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private boolean staying;
    private Vec3d interpolationTarget;
    private int interpolationSteps;
    private ItemStack companionStack = ItemStack.EMPTY;
    private Vec3d roamOffset;
    private int nextRoam;

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
    public void setCompanionStack(ItemStack stack) {
        companionStack = stack.isOf(AtlasMod.ATLAS) ? stack.copy() : ItemStack.EMPTY;
        if (!companionStack.isEmpty()) companionStack.setCount(1);
    }
    public ItemStack companionStack() {
        ItemStack stack = companionStack.isOf(AtlasMod.ATLAS) ? companionStack.copy() : new ItemStack(AtlasMod.ATLAS);
        stack.setCount(1);
        if (ownerId() != null) AtlasIdentity.stamp(stack, getUuid(), ownerId());
        return stack;
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwner(nbt.getUuid("Owner"));
        staying = nbt.getBoolean("Staying");
        if (nbt.contains("StoredItem")) setCompanionStack(ItemStack.fromNbt(nbt.getCompound("StoredItem")));
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId() != null) nbt.putUuid("Owner", ownerId());
        nbt.putBoolean("Staying", staying);
        nbt.putUuid("CompanionId", getUuid());
        nbt.put("StoredItem", companionStack().writeNbt(new NbtCompound()));
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
        if (isRemoved()) return;
        if (owner.getWorld() != world) {
            AtlasEntity replacement = new AtlasEntity(AtlasMod.ENTITY, owner.getWorld());
            replacement.setOwner(ownerId());
            replacement.setUuid(getUuid());
            replacement.setCompanionStack(companionStack());
            replacement.setPosition(owner.getPos().add(1.5, 1.05, 0));
            if (owner.getWorld().spawnEntity(replacement)) {
                discard();
                AtlasIdentity.deployed(owner, replacement);
                AtlasManager.remember(owner, replacement);
            }
            return;
        }
        if (!staying) {
            double angle = Math.toRadians(owner.getYaw() + 80);
            Vec3d offset = new Vec3d(Math.cos(angle) * 1.7, 1.05, Math.sin(angle) * 1.7);
            if (AtlasMod.CONFIG.autonomousFlight) {
                if (roamOffset == null || age >= nextRoam) {
                    double direction = random.nextDouble() * Math.PI * 2;
                    double radius = 1.5 + random.nextDouble() * 2.0;
                    Vec3d candidate = new Vec3d(Math.cos(direction) * radius, 1.0 + random.nextDouble() * 0.35, Math.sin(direction) * radius);
                    var box = getBoundingBox().offset(owner.getPos().add(candidate).subtract(getPos()));
                    roamOffset = world.isSpaceEmpty(this, box) ? candidate : offset;
                    nextRoam = age + 60 + random.nextInt(100);
                }
                offset = roamOffset;
            }
            Vec3d target = owner.getPos().add(offset).add(0, Math.sin(age * 0.065) * 0.12, 0);
            double distance = getPos().squaredDistanceTo(target);
            setPosition(distance > 256 ? target : getPos().lerp(target, AtlasMod.CONFIG.autonomousFlight ? 0.12 : 0.18));
        }
        var binding = AtlasCompanionState.get(world.getServer()).binding(ownerId());
        if (binding == null || age % 20 == 0 || (getBlockX() >> 4) != (binding.pos().getX() >> 4)
                || (getBlockZ() >> 4) != (binding.pos().getZ() >> 4)) AtlasIdentity.deployed(owner, this);
    }
    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        interpolationTarget = new Vec3d(x, y, z);
        interpolationSteps = Math.max(1, steps);
    }
    @Override public boolean canHit() { return true; }
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public void kill() { /* The companion can only be recalled or unloaded, never killed. */ }
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
