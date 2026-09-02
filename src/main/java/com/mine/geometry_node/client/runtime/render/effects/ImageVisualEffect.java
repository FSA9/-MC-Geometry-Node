package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.client.runtime.render.image.ClientImageAssetManager;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawImageVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A temporary textured plane placed in world space. */
public final class ImageVisualEffect extends AbstractVisualEffect {
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    private final String imageSource;
    private final String imageReference;
    private final String sizeMode;
    private final LiveValue.State<Vec3> position;
    private final LiveValue.State<Vec3> rotation;
    private final LiveValue.State<Float> width;
    private final LiveValue.State<Float> height;
    private final LiveValue.State<Float> alpha;
    private Identifier texture;
    private boolean textureResolved;
    private float imageAspect;
    private boolean imageAspectResolved;

    public ImageVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData() != null ? packet.extraData() : new CompoundTag();
        this.imageSource = data.getStringOr("imageSource", "");
        this.imageReference = data.getStringOr("imageRef", "");
        Vec3 basePosition = new Vec3(
                data.getDoubleOr("posX", 0.0),
                data.getDoubleOr("posY", 0.0),
                data.getDoubleOr("posZ", 0.0));
        Vec3 baseRotation = new Vec3(
                data.getDoubleOr("rotX", 0.0),
                data.getDoubleOr("rotY", 0.0),
                data.getDoubleOr("rotZ", 0.0));
        this.sizeMode = data.getStringOr("sizeMode", "stretch");
        float baseWidth = Math.max(0.01F, data.getFloatOr("width", 1.0F));
        float baseHeight = Math.max(0.01F, data.getFloatOr("height", 1.0F));
        float baseAlpha = Math.clamp(data.getFloatOr("alpha", 1.0F), 0.0F, 1.0F);
        this.position = captureXyz(DrawImageVisual.POSITION_PORT, "position", basePosition);
        this.rotation = captureXyz(DrawImageVisual.ROTATION_PORT, "rotation", baseRotation);
        this.width = captureFloat(DrawImageVisual.WIDTH_PORT, "width", baseWidth);
        this.height = captureFloat(DrawImageVisual.HEIGHT_PORT, "height", baseHeight);
        this.alpha = captureFloat(DrawImageVisual.ALPHA_PORT, "alpha", baseAlpha);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                       SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        if (!textureResolved) {
            texture = ClientImageAssetManager.resolve(imageSource, imageReference);
            textureResolved = true;
        }
        if (texture == null) return;

        ExpressionEvaluationContext context = expressionContext(partialTick);
        Vec3 evaluatedPosition = position.evaluate(context);
        Vec3 evaluatedRotation = rotation.evaluate(context);
        float evaluatedWidth = Math.max(0.01F, Math.abs(width.evaluate(context)));
        float evaluatedHeight = Math.max(0.01F, Math.abs(height.evaluate(context)));
        int evaluatedAlpha = Math.clamp(Math.round(alpha.evaluate(context) * 255.0F), 0, 255);
        if (evaluatedAlpha <= 0) return;
        DisplaySize displaySize = displaySize(evaluatedWidth, evaluatedHeight);

        Vec3 renderPosition = evaluatedPosition.subtract(camPos);
        poseStack.pushPose();
        poseStack.translate(renderPosition.x, renderPosition.y, renderPosition.z);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-evaluatedRotation.y),
                (float) Math.toRadians(evaluatedRotation.x),
                (float) Math.toRadians(evaluatedRotation.z)));

        float halfWidth = displaySize.width() * 0.5F;
        float halfHeight = displaySize.height() * 0.5F;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucentEmissive(texture, false),
                (pose, vertices) -> {
                    vertices.addVertex(pose, -halfWidth, halfHeight, 0.0F)
                            .setColor(255, 255, 255, evaluatedAlpha).setUv(0.0F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT)
                            .setNormal(pose, 0.0F, 0.0F, 1.0F);
                    vertices.addVertex(pose, -halfWidth, -halfHeight, 0.0F)
                            .setColor(255, 255, 255, evaluatedAlpha).setUv(0.0F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT)
                            .setNormal(pose, 0.0F, 0.0F, 1.0F);
                    vertices.addVertex(pose, halfWidth, -halfHeight, 0.0F)
                            .setColor(255, 255, 255, evaluatedAlpha).setUv(1.0F, 1.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT)
                            .setNormal(pose, 0.0F, 0.0F, 1.0F);
                    vertices.addVertex(pose, halfWidth, halfHeight, 0.0F)
                            .setColor(255, 255, 255, evaluatedAlpha).setUv(1.0F, 0.0F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT)
                            .setNormal(pose, 0.0F, 0.0F, 1.0F);
                });
        poseStack.popPose();
    }

    private DisplaySize displaySize(float width, float height) {
        if (!"fit".equals(sizeMode)) return new DisplaySize(width, height);
        float aspect = imageAspect();
        if (aspect <= 0.0F) return new DisplaySize(width, height);
        float boundsAspect = width / height;
        return aspect > boundsAspect
                ? new DisplaySize(width, width / aspect)
                : new DisplaySize(height * aspect, height);
    }

    private float imageAspect() {
        if (imageAspectResolved) return imageAspect;
        imageAspectResolved = true;
        var abstractTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (!(abstractTexture instanceof DynamicTexture dynamicTexture)) return 0.0F;
        var pixels = dynamicTexture.getPixels();
        if (pixels == null || pixels.isClosed() || pixels.getWidth() <= 0 || pixels.getHeight() <= 0) return 0.0F;
        imageAspect = pixels.getWidth() / (float) pixels.getHeight();
        return imageAspect;
    }

    private record DisplaySize(float width, float height) {
    }
}
