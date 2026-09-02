package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawItemVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ItemVisualEffect extends AbstractVisualEffect {
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    private final int sourceEntityId;
    private ItemStack itemStack = ItemStack.EMPTY;
    private ItemDisplayContext displayContext = ItemDisplayContext.FIXED;

    private final Vec3 bTrans;
    private final Vec3 bRot;
    private final Vec3 bScale;
    private final LiveValue.State<Vec3> translation;
    private final LiveValue.State<Vec3> rotation;
    private final LiveValue.State<Vec3> scale;

    public ItemVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();

        if (data != null) {
            this.sourceEntityId = data.getIntOr("sourceId", -1);

            this.bTrans = new Vec3(data.getDoubleOr("bTransX", 0.0), data.getDoubleOr("bTransY", 0.0), data.getDoubleOr("bTransZ", 0.0));
            this.bRot = new Vec3(data.getDoubleOr("bRotX", 0.0), data.getDoubleOr("bRotY", 0.0), data.getDoubleOr("bRotZ", 0.0));
            this.bScale = new Vec3(data.getDoubleOr("bScaleX", 1.0), data.getDoubleOr("bScaleY", 1.0), data.getDoubleOr("bScaleZ", 1.0));

            String contextStr = data.getStringOr("item_display", "fixed");
            try {
                this.displayContext = ItemDisplayContext.valueOf(contextStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.displayContext = ItemDisplayContext.FIXED;
            }

            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && data.contains("item")) {
                CompoundTag itemTag = data.getCompoundOrEmpty("item");
                this.itemStack = ItemStack.OPTIONAL_CODEC
                        .parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), itemTag)
                        .result()
                        .orElse(ItemStack.EMPTY);

                if (this.itemStack.isEmpty()) {
                    System.err.println("[ItemVisualEffect] 警告：客户端接收到的物品为空！");
                }
            }
        } else {
            this.sourceEntityId = -1;
            this.bTrans = Vec3.ZERO; this.bRot = Vec3.ZERO; this.bScale = new Vec3(1, 1, 1);
        }
        this.translation = captureXyz(DrawItemVisual.TRANSLATION_PORT, "translation", bTrans);
        this.rotation = captureXyz(DrawItemVisual.ROTATION_PORT, "rotation", bRot);
        this.scale = captureXyz(DrawItemVisual.SCALE_PORT, "scale", bScale);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        if (this.itemStack.isEmpty()) return;

        ClientLevel level = Minecraft.getInstance().level;

        // 【核心逻辑】：默认锚点是世界原点 (0,0,0)
        Vec3 anchorPos = Vec3.ZERO;

        // 如果绑定了实体，锚点就变成实体的当前坐标
        if (level != null && this.sourceEntityId != -1) {
            Entity entity = level.getEntity(this.sourceEntityId);
            if (entity != null) {
                anchorPos = entity.getPosition(partialTick);
            } else {
                return; // 实体暂时离开渲染距离，跳过渲染
            }
        }

        ExpressionEvaluationContext expressionContext = expressionContext(partialTick);
        Vec3 trans = translation.evaluate(expressionContext);
        Vec3 rot = rotation.evaluate(expressionContext);
        Vec3 evaluatedScale = scale.evaluate(expressionContext);

        // 最终坐标 = 锚点 + 偏移 - 相机坐标
        Vec3 renderPos = anchorPos.add(trans).subtract(camPos);

        poseStack.pushPose();
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z);

        Quaternionf rotationQuat = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-rot.y),
                (float) Math.toRadians(rot.x),
                (float) Math.toRadians(rot.z)
        );
        poseStack.mulPose(rotationQuat);
        poseStack.scale((float) evaluatedScale.x, (float) evaluatedScale.y, (float) evaluatedScale.z);

        ItemStackRenderState renderState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                renderState,
                this.itemStack,
                this.displayContext,
                level,
                null,
                sourceEntityId
        );
        renderState.submit(
                poseStack,
                submitNodeCollector,
                FULL_BRIGHT_LIGHT,
                OverlayTexture.NO_OVERLAY,
                0
        );

        poseStack.popPose();
    }
}
