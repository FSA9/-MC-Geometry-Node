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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class ItemVisualEffect extends AbstractVisualEffect {
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    private final int renderSeed;
    private final VisualTransform transform;
    private ItemStack itemStack = ItemStack.EMPTY;
    private ItemDisplayContext displayContext = ItemDisplayContext.FIXED;

    private final Vec3 bScale;
    private final LiveValue.State<Vec3> scale;

    public ItemVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        this.transform = VisualTransform.capture(this,data);
        var anchor = transform.anchor().anchor();
        this.renderSeed = anchor.entityId() == null ? 0 : anchor.entityId().hashCode();

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

        this.scale = captureXyz(DrawItemVisual.SCALE_PORT, "scale", bScale);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        if (this.itemStack.isEmpty()) return;

        ClientLevel level = Minecraft.getInstance().level;

        ExpressionEvaluationContext expressionContext = expressionContext(partialTick);
        VisualTransform.Frame frame = transform.resolve(level,partialTick,expressionContext);
        if(frame==null)return;
        Vec3 evaluatedScale = scale.evaluate(expressionContext);
        Vec3 renderPos = frame.position().subtract(camPos);
        poseStack.pushPose();
        poseStack.translate(renderPos.x,renderPos.y,renderPos.z);
        poseStack.mulPose(new Quaternionf(frame.orientation()));
        poseStack.scale((float) evaluatedScale.x, (float) evaluatedScale.y, (float) evaluatedScale.z);

        ItemStackRenderState renderState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                renderState,
                this.itemStack,
                this.displayContext,
                level,
                null,
                renderSeed
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
