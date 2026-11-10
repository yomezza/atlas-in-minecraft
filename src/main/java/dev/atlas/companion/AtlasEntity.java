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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public final class AtlasEntity extends Entity {
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TARGET_SCALE = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> LASER_TARGET = DataTracker.registerData(AtlasEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private boolean staying;
    private Vec3d interpolationTarget;
    private int interpolationSteps;
    private ItemStack companionStack = ItemStack.EMPTY;
    private Vec3d roamOffset;
    private int nextRoam;
    private float scaleStart = 1, visualScale = 1, previousVisualScale = 1;
    private int scaleAge, scaleDuration;
    private double stayBaseY = Double.NaN;
    private byte flightMode = -1;
    private final java.util.Deque<UUID> attackTargets = new java.util.ArrayDeque<>();
    private int laserAge;

    public AtlasEntity(EntityType<? extends AtlasEntity> type, World world) {
        super(type, world);
        noClip = true;
        setNoGravity(true);
        setInvulnerable(true);
    }
    @Override protected void initDataTracker() {
        dataTracker.startTracking(OWNER, Optional.empty());
        dataTracker.startTracking(SCALE, 1.0f); dataTracker.startTracking(TARGET_SCALE, 1.0f);
        dataTracker.startTracking(LASER_TARGET, -1);
    }
    public UUID ownerId() { return dataTracker.get(OWNER).orElse(null); }
    public void setOwner(UUID uuid) { dataTracker.set(OWNER, Optional.ofNullable(uuid)); }
    public void setStaying(boolean value) {
        staying = value; stayBaseY = value ? getY() - (AtlasScale.hoverHeight(currentScale()) - 1.05) : Double.NaN;
        flightMode = (byte)(value ? 2 : -1);
    }
    public int flightMode() { return staying ? 2 : (flightMode < 0 ? (AtlasMod.CONFIG.autonomousFlight ? 0 : 1) : flightMode); }
    public void setFlightMode(int mode) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("flight mode");
        setStaying(mode == 2); flightMode = (byte)mode;
    }
    public boolean silkMode() { return AtlasTool.silk(companionStack()); }
    public void setSilkMode(boolean value) { companionStack = companionStack(); AtlasTool.setSilk(companionStack, value); }
    public void startAttack(java.util.List<UUID> targets) { stopAttack(); attackTargets.addAll(targets); }
    public void stopAttack() { attackTargets.clear(); laserAge = 0; dataTracker.set(LASER_TARGET, -1); }
    public boolean attacking() { return !attackTargets.isEmpty(); }
    public net.minecraft.entity.Entity laserTarget() { return getWorld().getEntityById(dataTracker.get(LASER_TARGET)); }
    private void tickAttack(ServerPlayerEntity owner) {
        if (!AtlasManager.allowed(owner)) { stopAttack(); return; }
        while (!attackTargets.isEmpty()) {
            var entity = ((ServerWorld)getWorld()).getEntity(attackTargets.peek());
            if (!AtlasCombat.enemy(entity, owner) || entity.squaredDistanceTo(owner) > AtlasCombat.RANGE * AtlasCombat.RANGE || !owner.canSee(entity)) {
                attackTargets.remove(); laserAge = 0; continue;
            }
            net.minecraft.entity.LivingEntity victim = (net.minecraft.entity.LivingEntity)entity;
            dataTracker.set(LASER_TARGET, victim.getId());
            if (++laserAge % 10 == 6) {
                victim.setOnFireFor(4); victim.timeUntilRegen = 0;
                victim.damage(AtlasCombat.energy(victim, this, owner), 24);
                ((ServerWorld)getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 16, 0.25, 0.35, 0.25, 0.03);
            }
            return;
        }
        if (dataTracker.get(LASER_TARGET) >= 0) {
            dataTracker.set(LASER_TARGET, -1); AtlasManager.say(owner, "Подтверждённые цели устранены или вышли из зоны видимости. Луч выключен.");
        }
    }
    public float currentScale() { return dataTracker.get(SCALE); }
    public float targetScale() { return dataTracker.get(TARGET_SCALE); }
    public float renderScale(float tickDelta) { return MathHelper.lerp(tickDelta, previousVisualScale, visualScale); }
    public void setTargetScale(float value) {
        int maximum = AtlasMod.CONFIG == null ? 128 : AtlasMod.CONFIG.maxCompanionScale;
        dataTracker.set(TARGET_SCALE, AtlasScale.validate(value, maximum));
        scaleStart = currentScale(); scaleAge = 0;
        scaleDuration = 20 * (AtlasMod.CONFIG == null ? 4 : AtlasMod.CONFIG.growthDurationSeconds);
    }
    private void restoreScale(float current, float target) {
        dataTracker.set(SCALE, AtlasScale.sanitize(current, AtlasMod.CONFIG.maxCompanionScale));
        visualScale = previousVisualScale = currentScale();
        setTargetScale(AtlasScale.sanitize(target, AtlasMod.CONFIG.maxCompanionScale));
    }
    private void advanceScale() {
        if (targetScale() > AtlasMod.CONFIG.maxCompanionScale) setTargetScale(AtlasMod.CONFIG.maxCompanionScale);
        if (currentScale() == targetScale()) return;
        float time = Math.min(1, ++scaleAge / (float)Math.max(1, scaleDuration));
        float eased = time * time * (3 - 2 * time);
        dataTracker.set(SCALE, time == 1 ? targetScale() : MathHelper.lerp(eased, scaleStart, targetScale()));
    }
    public void setCompanionStack(ItemStack stack) {
        companionStack = stack.isOf(AtlasMod.ATLAS) ? stack.copy() : ItemStack.EMPTY;
        if (!companionStack.isEmpty()) companionStack.setCount(1);
        if (!companionStack.isEmpty()) setTargetScale(AtlasScale.sanitize(
                companionStack.hasNbt() && companionStack.getNbt().contains(AtlasScale.ITEM_KEY) ? companionStack.getNbt().getFloat(AtlasScale.ITEM_KEY) : 1,
                AtlasMod.CONFIG.maxCompanionScale));
        flightMode = companionStack.hasNbt() && companionStack.getNbt().contains("AtlasFlightMode") ? companionStack.getNbt().getByte("AtlasFlightMode") : -1;
        staying = flightMode == 2; stayBaseY = Double.NaN;
    }
    public ItemStack companionStack() {
        ItemStack stack = companionStack.isOf(AtlasMod.ATLAS) ? companionStack.copy() : new ItemStack(AtlasMod.ATLAS);
        stack.setCount(1);
        if (ownerId() != null) AtlasIdentity.stamp(stack, getUuid(), ownerId());
        stack.getOrCreateNbt().putFloat(AtlasScale.ITEM_KEY, targetScale());
        if (flightMode >= 0) stack.getOrCreateNbt().putByte("AtlasFlightMode", flightMode);
        return stack;
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Owner")) setOwner(nbt.getUuid("Owner"));
        staying = nbt.getBoolean("Staying");
        if (nbt.contains("StoredItem")) setCompanionStack(ItemStack.fromNbt(nbt.getCompound("StoredItem")));
        staying = nbt.getBoolean("Staying");
        float target = nbt.contains("TargetScale") ? nbt.getFloat("TargetScale") : targetScale();
        restoreScale(nbt.contains("Scale") ? nbt.getFloat("Scale") : target, target);
        if (staying) stayBaseY = nbt.contains("StayBaseY") ? nbt.getDouble("StayBaseY") : getY() - (AtlasScale.hoverHeight(currentScale()) - 1.05);
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (ownerId() != null) nbt.putUuid("Owner", ownerId());
        nbt.putBoolean("Staying", staying);
        nbt.putUuid("CompanionId", getUuid());
        nbt.put("StoredItem", companionStack().writeNbt(new NbtCompound()));
        nbt.putFloat("Scale", currentScale()); nbt.putFloat("TargetScale", targetScale());
        if (staying && Double.isFinite(stayBaseY)) nbt.putDouble("StayBaseY", stayBaseY);
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) {
            previousVisualScale = visualScale;
            visualScale = MathHelper.lerp(0.5f, visualScale, currentScale());
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
        advanceScale();
        tickAttack(owner);
        if (owner.getWorld() != world) {
            AtlasEntity replacement = new AtlasEntity(AtlasMod.ENTITY, owner.getWorld());
            replacement.setOwner(ownerId());
            replacement.setUuid(getUuid());
            replacement.setCompanionStack(companionStack());
            replacement.restoreScale(currentScale(), targetScale());
            replacement.setPosition(owner.getPos().add(1.5, AtlasScale.hoverHeight(currentScale()), 0));
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
            if (flightMode() == 0) {
                if (roamOffset == null || age >= nextRoam) {
                    double direction = random.nextDouble() * Math.PI * 2;
                    double radius = 1.5 + random.nextDouble() * 2.0;
                    Vec3d candidate = new Vec3d(Math.cos(direction) * radius, 1.0 + random.nextDouble() * 0.35, Math.sin(direction) * radius);
                    var box = getBoundingBox().offset(owner.getPos().add(candidate).subtract(getPos()));
                    box = box.offset(0, AtlasScale.hoverHeight(currentScale()) - 1.05, 0);
                    roamOffset = world.isSpaceEmpty(this, box) ? candidate : offset;
                    nextRoam = age + 60 + random.nextInt(100);
                }
                offset = roamOffset;
            }
            Vec3d target = owner.getPos().add(offset).add(0, AtlasScale.hoverHeight(currentScale()) - 1.05 + Math.sin(age * 0.065) * 0.12, 0);
            double distance = getPos().squaredDistanceTo(target);
            double teleportDistance = Math.max(16, (currentScale() + targetScale()) * 2);
            setPosition(distance > teleportDistance * teleportDistance ? target : getPos().lerp(target, flightMode() == 0 ? 0.12 : 0.18));
        } else {
            if (!Double.isFinite(stayBaseY)) stayBaseY = getY() - (AtlasScale.hoverHeight(currentScale()) - 1.05);
            setPosition(getX(), MathHelper.lerp(0.12, getY(), stayBaseY + AtlasScale.hoverHeight(currentScale()) - 1.05), getZ());
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
    @Override public boolean shouldRender(double distance) {
        double range = 192 + Math.max(currentScale(), targetScale()) * 3;
        return distance < range * range;
    }
    @Override public Box getVisibilityBoundingBox() {
        double scale = Math.max(currentScale(), Math.max(visualScale, previousVisualScale));
        Box box = new Box(getX() - 0.66 * scale, getY() - 0.18 * scale, getZ() - 0.66 * scale,
                getX() + 0.66 * scale, getY() + 0.98 * scale, getZ() + 0.66 * scale);
        var target = laserTarget();
        return target == null ? box : box.union(target.getBoundingBox());
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data.equals(SCALE) && age <= 1) visualScale = previousVisualScale = currentScale();
    }
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public void kill() { /* The companion can only be recalled or unloaded, never killed. */ }
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
