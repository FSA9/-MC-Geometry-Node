package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ItemVisualEffect extends AbstractVisualEffect {

    private final int sourceEntityId;
    private ItemStack itemStack = ItemStack.EMPTY;
    private ItemDisplayContext displayContext = ItemDisplayContext.FIXED;

    private final Vec3 bTrans;
    private final Vec3 bRot;
    private final Vec3 bScale;

    public ItemVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();

        if (data != null) {
            this.sourceEntityId = data.contains("sourceId") ? data.getInt("sourceId") : -1;

            this.bTrans = new Vec3(data.getDouble("bTransX"), data.getDouble("bTransY"), data.getDouble("bTransZ"));
            this.bRot = new Vec3(data.getDouble("bRotX"), data.getDouble("bRotY"), data.getDouble("bRotZ"));
            this.bScale = new Vec3(data.getDouble("bScaleX"), data.getDouble("bScaleY"), data.getDouble("bScaleZ"));

            String contextStr = data.getString("item_display");
            try {
                this.displayContext = ItemDisplayContext.valueOf(contextStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.displayContext = ItemDisplayContext.FIXED;
            }

            ClientLevel level = Minecraft.getInstance().level;
            if (level != null && data.contains("item")) {
                CompoundTag itemTag = data.getCompound("item");
                this.itemStack = ItemStack.parseOptional(level.registryAccess(), itemTag);

                if (this.itemStack.isEmpty()) {
                    System.err.println("[ItemVisualEffect] 警告：客户端接收到的物品为空！");
                }
            }
        } else {
            this.sourceEntityId = -1;
            this.bTrans = Vec3.ZERO; this.bRot = Vec3.ZERO; this.bScale = new Vec3(1, 1, 1);
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        if (this.itemStack.isEmpty()) return;

        updateVariables(partialTick);

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

        double tX = eval("transX", Double.NaN); double tY = eval("transY", Double.NaN); double tZ = eval("transZ", Double.NaN);
        Vec3 trans = new Vec3(
                Double.isNaN(tX) ? bTrans.x : tX,
                Double.isNaN(tY) ? bTrans.y : tY,
                Double.isNaN(tZ) ? bTrans.z : tZ
        );

        double rX = eval("rotX", Double.NaN); double rY = eval("rotY", Double.NaN); double rZ = eval("rotZ", Double.NaN);
        Vec3 rot = new Vec3(
                Double.isNaN(rX) ? bRot.x : rX,
                Double.isNaN(rY) ? bRot.y : rY,
                Double.isNaN(rZ) ? bRot.z : rZ
        );

        double sX = eval("scaleX", Double.NaN); double sY = eval("scaleY", Double.NaN); double sZ = eval("scaleZ", Double.NaN);
        Vec3 scale = new Vec3(
                Double.isNaN(sX) ? bScale.x : sX,
                Double.isNaN(sY) ? bScale.y : sY,
                Double.isNaN(sZ) ? bScale.z : sZ
        );

        // 最终坐标 = 锚点 + 偏移 - 相机坐标
        Vec3 renderPos = anchorPos.add(trans).subtract(camPos);

        poseStack.pushPose();
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z);

        Quaternionf rotationQuat = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rot.y),
                (float) Math.toRadians(rot.x),
                (float) Math.toRadians(rot.z)
        );
        poseStack.mulPose(rotationQuat);
        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);

        int light = LightTexture.FULL_BRIGHT;

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.itemStack,
                this.displayContext,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                level,
                sourceEntityId
        );

        poseStack.popPose();

        // 强制刷新缓冲区
        if (bufferSource instanceof MultiBufferSource.BufferSource mainBuffer) {
            mainBuffer.endBatch();
        }
    }
}