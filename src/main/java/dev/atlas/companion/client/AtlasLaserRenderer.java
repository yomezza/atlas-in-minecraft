package dev.atlas.companion.client;

import dev.atlas.companion.AtlasEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

public final class AtlasLaserRenderer {
    private AtlasLaserRenderer() {}
    public static void render(AtlasEntity atlas, float tickDelta, MatrixStack matrices, VertexConsumerProvider provider) {
        var target = atlas.laserTarget(); if (target == null) return;
        float scale = atlas.renderScale(tickDelta);
        Vector3f start = new Vector3f(0, 0.4f * scale, 0);
        Vector3f end = new Vector3f((float)(MathHelper.lerp(tickDelta, target.prevX, target.getX()) - MathHelper.lerp(tickDelta, atlas.prevX, atlas.getX())),
                (float)(MathHelper.lerp(tickDelta, target.prevY, target.getY()) + target.getHeight() * 0.6 - MathHelper.lerp(tickDelta, atlas.prevY, atlas.getY())),
                (float)(MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - MathHelper.lerp(tickDelta, atlas.prevZ, atlas.getZ())));
        Vector3f direction = new Vector3f(end).sub(start).normalize();
        Vector3f side = new Vector3f(direction).cross(0, 1, 0);
        if (side.lengthSquared() < 0.001) side.set(1, 0, 0); else side.normalize();
        Vector3f up = new Vector3f(direction).cross(side).normalize();
        float radius = Math.min(0.6f, 0.045f + scale * 0.003f);
        VertexConsumer out = provider.getBuffer(RenderLayer.getLightning());
        prism(matrices, out, start, end, side, up, radius, 255, 30, 8, 100);
        prism(matrices, out, start, end, side, up, radius * 0.28f, 255, 225, 170, 255);
    }
    private static void prism(MatrixStack matrices, VertexConsumer out, Vector3f start, Vector3f end, Vector3f side, Vector3f up,
            float radius, int red, int green, int blue, int alpha) {
        Vector3f[] offsets = {new Vector3f(side).add(up).mul(radius), new Vector3f(side).sub(up).mul(radius),
                new Vector3f(side).negate().sub(up).mul(radius), new Vector3f(side).negate().add(up).mul(radius)};
        for (int i = 0; i < 4; i++) {
            Vector3f a = offsets[i], b = offsets[(i + 1) % 4];
            for (Vector3f point : new Vector3f[]{new Vector3f(start).add(a), new Vector3f(end).add(a), new Vector3f(end).add(b), new Vector3f(start).add(b)})
                out.vertex(matrices.peek().getPositionMatrix(), point.x, point.y, point.z).color(red, green, blue, alpha).next();
        }
    }
}
