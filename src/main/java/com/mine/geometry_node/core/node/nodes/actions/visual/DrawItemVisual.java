package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public class DrawItemVisual extends BaseNode {

    public static final String TYPE_ID = "draw_item_visual";
    public static final String PROPERTY_DISPLAY_CONTEXT = "item_display_context";
    public static final PortDef TRANSLATION_PORT = StandardPorts.TRANSLATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef ROTATION_PORT = StandardPorts.ROTATION.toInput(Vec3.ZERO).liveExpression();
    public static final PortDef SCALE_PORT = StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_item_visual"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.STRING, "display_context")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 绑定核心实体
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, new String[]{"fixed", "none", "third_person_left_hand", "third_person_right_hand", "first_person_left_hand", "first_person_right_hand", "head", "gui", "ground"})
                ))
                // 核心三大动态矢量
                .addRow(new PortRow(TRANSLATION_PORT, null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(ROTATION_PORT, null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(SCALE_PORT, null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(20), null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.NUMERIC_MIN, 0)))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        Entity sourceEntity = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        int sourceId = sourceEntity != null ? sourceEntity.getId() : -1;

        ItemStack itemStack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        if (itemStack == null || itemStack.isEmpty()) itemStack = new ItemStack(Items.STONE);

        String displayContext = getInput(context, StandardPorts.STRING.getId(), String.class);
        if (displayContext == null) displayContext = "fixed";

        // 获取基础的死数值偏移
        Vec3 baseTrans = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Vec3 baseRot = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        Vec3 baseScale = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (baseTrans == null) baseTrans = Vec3.ZERO;
        if (baseRot == null) baseRot = Vec3.ZERO;
        if (baseScale == null) baseScale = new Vec3(1, 1, 1);

        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        if (duration == null || duration <= 0) duration = 20;

        Map<String, ExpressionData> expressions = new LinkedHashMap<>();
        putInputExpression(context, StandardPorts.TRANSLATION.getId(), "translation", expressions);
        putInputExpression(context, StandardPorts.ROTATION.getId(), "rotation", expressions);
        putInputExpression(context, StandardPorts.SIZE_3.getId(), "scale", expressions);

        CompoundTag extraData = new CompoundTag();
        extraData.putInt("sourceId", sourceId);

        // 写入保底底数 (彻底移除了 StartX/Y/Z)
        extraData.putDouble("bTransX", baseTrans.x); extraData.putDouble("bTransY", baseTrans.y); extraData.putDouble("bTransZ", baseTrans.z);
        extraData.putDouble("bRotX", baseRot.x); extraData.putDouble("bRotY", baseRot.y); extraData.putDouble("bRotZ", baseRot.z);
        extraData.putDouble("bScaleX", baseScale.x); extraData.putDouble("bScaleY", baseScale.y); extraData.putDouble("bScaleZ", baseScale.z);

        extraData.putString("item_display", displayContext);

        // 1.21 物品序列化
        net.minecraft.nbt.Tag itemTag = ItemStack.OPTIONAL_CODEC
                .encodeStart(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), itemStack)
                .result()
                .orElseGet(CompoundTag::new);
        extraData.put("item", itemTag);

        context.broadcastDynamicVisual("item_visual", 0xFFFFFFFF, duration, expressions, extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
