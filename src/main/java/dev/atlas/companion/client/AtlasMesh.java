package dev.atlas.companion.client;

import dev.atlas.companion.AtlasMod;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Eight real triangular faces: no billboard and no external model library. */
public final class AtlasMesh {
    private static final Identifier TEXTURE = AtlasMod.id("textures/entity/atlas.png");
    private static final Vector3f[] POINTS = {
            new Vector3f(0, 0.85f, 0), new Vector3f(0.60f, 0, 0), new Vector3f(0, 0, 0.60f),
            new Vector3f(-0.60f, 0, 0), new Vector3f(0, 0, -0.60f), new Vector3f(0, -0.85f, 0)};
    private static final int[][] FACES = {{0,1,2},{0,2,3},{0,3,4},{0,4,1},{5,2,1},{5,3,2},{5,4,3},{5,1,4}};
    private AtlasMesh() {}
    public static void render(MatrixStack matrices, VertexConsumerProvider provider, int light, float time) {
        VertexConsumer consumer = provider.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        // The inner crystal stays visible through the gaps between the floating armour panels.
        float pulse = 0.68f + (float)Math.sin(time * 0.08) * 0.085f;
        octahedron(matrices, consumer, pulse, 1, true, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        octahedron(matrices, consumer, 1, 0.88f, false, light);
        for (int i = 0; i < 3; i++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-time * 3 + i * 120));
            matrices.translate(0.88, Math.sin(time * 0.06 + i * 2) * 0.12, 0);
            matrices.scale(0.11f, 0.11f, 0.11f);
            octahedron(matrices, consumer, 1, 1, true, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
    }
    private static void octahedron(MatrixStack matrices, VertexConsumer consumer, float size, float inset,
                                   boolean core, int light) {
        for (int[] face : FACES) {
            Vector3f a = new Vector3f(POINTS[face[0]]).mul(size);
            Vector3f b = new Vector3f(POINTS[face[1]]).mul(size);
            Vector3f c = new Vector3f(POINTS[face[2]]).mul(size);
            Vector3f center = new Vector3f(a).add(b).add(c).div(3);
            a.sub(center).mul(inset).add(center); b.sub(center).mul(inset).add(center); c.sub(center).mul(inset).add(center);
            Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
            float offset = core ? 0.5f : 0;
            vertex(matrices, consumer, a, normal, offset + 0.25f, 2/128f, light);
            vertex(matrices, consumer, b, normal, offset + 2/128f, 62/128f, light);
            vertex(matrices, consumer, c, normal, offset + 62/128f, 62/128f, light);
            vertex(matrices, consumer, c, normal, offset + 62/128f, 62/128f, light);
        }
    }
    private static void vertex(MatrixStack matrices, VertexConsumer out, Vector3f point, Vector3f normal,
                               float u, float v, int light) {
        MatrixStack.Entry entry = matrices.peek();
        out.vertex(entry.getPositionMatrix(), point.x, point.y, point.z).color(255,255,255,255)
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(entry.getNormalMatrix(), normal.x, normal.y, normal.z).next();
    }
}
