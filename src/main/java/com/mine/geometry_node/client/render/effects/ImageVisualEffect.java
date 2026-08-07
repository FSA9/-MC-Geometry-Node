package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.client.render.image.ClientImageAssetManager;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
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
    private final Vec3 position;
    private final Vec3 rotation;
    private final String sizeMode;
    private final float width;
    private final float height;
    private final int alpha;
    private Identifier texture;
    private boolean textureResolved;
    private float displayWidth;
    private float displayHeight;
    private boolean displaySizeResolved;

    public ImageVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData() != null ? packet.extraData() : new CompoundTag();
        this.imageSource = data.getStringOr("imageSource", "");
        this.imageReference = data.getStringOr("imageRef", "");
        this.position = new Vec3(
                data.getDoubleOr("posX", 0.0),
                data.getDoubleOr("posY", 0.0),
                data.getDoubleOr("posZ", 0.0)
        );
        this.rotation = new Vec3(
                data.getDoubleOr("rotX", 0.0),
                data.getDoubleOr("rotY", 0.0),
                data.getDoubleOr("rotZ", 0.0)
        );
        this.sizeMode = data.getStringOr("sizeMode", "stretch");
        this.width = Math.max(0.01f, data.getFloatOr("width", 1.0f));
        this.height = Math.max(0.01f, data.getFloatOr("height", 1.0f));
        this.displayWidth = this.width;
        this.displayHeight = this.height;
        this.alpha = Math.clamp(Math.round(data.getFloatOr("alpha", 1.0f) * 255.0f), 0, 255);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                       SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        if (!textureResolved) {
            texture = ClientImageAssetManager.resolve(imageSource, imageReference);
            textureResolved = true;
        }
        if (texture == null || alpha <= 0) {
            return;
        }
        resolveDisplaySize();

        Vec3 renderPosition = position.subtract(camPos);
        poseStack.pushPose();
        poseStack.translate(renderPosition.x, renderPosition.y, renderPosition.z);
        poseStack.mulPose(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        ));

        float halfWidth = displayWidth * 0.5f;
        float halfHeight = displayHeight * 0.5f;
        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucentEmissive(texture, false),
                (pose, vertices) -> {
                    vertices.addVertex(pose, -halfWidth, halfHeight, 0.0f)
                            .setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
                    vertices.addVertex(pose, -halfWidth, -halfHeight, 0.0f)
                            .setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
                    vertices.addVertex(pose, halfWidth, -halfHeight, 0.0f)
                            .setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
                    vertices.addVertex(pose, halfWidth, halfHeight, 0.0f)
                            .setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT_LIGHT).setNormal(pose, 0.0f, 0.0f, 1.0f);
                }
        );
        poseStack.popPose();
    }

    private void resolveDisplaySize() {
        if (displaySizeResolved) return;
        displaySizeResolved = true;
        if (!"fit".equals(sizeMode)) return;

        var abstractTexture = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (!(abstractTexture instanceof DynamicTexture dynamicTexture)) return;
        var pixels = dynamicTexture.getPixels();
        if (pixels == null || pixels.isClosed() || pixels.getWidth() <= 0 || pixels.getHeight() <= 0) return;

        float imageAspect = pixels.getWidth() / (float) pixels.getHeight();
        float boundsAspect = width / height;
        if (imageAspect > boundsAspect) {
            displayWidth = width;
            displayHeight = width / imageAspect;
        } else {
            displayHeight = height;
            displayWidth = height * imageAspect;
        }
    }
}
