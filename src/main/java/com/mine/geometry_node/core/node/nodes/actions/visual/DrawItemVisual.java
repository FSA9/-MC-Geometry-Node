package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.VisualSpatialAnchors;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.util.VisualTransformInputs;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class DrawItemVisual extends BaseNode {

    public static final String TYPE_ID = "draw_item_visual";
    public static final String PROPERTY_DISPLAY_CONTEXT = "item_display_context";
    public static final PortDef SCALE_PORT = StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)).liveExpression();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_item_visual"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.SPATIAL_ANCHOR, "geometry_node.anchor.help")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.STRING, "display_context")
                        .input(AnchorNodeSupport.WORLD_OFFSET.id(), "geometry_node.transform.world_offset.help")
                        .input(AnchorNodeSupport.WORLD_ROTATION.id(), "geometry_node.transform.world_rotation.help")
                        .input(AnchorNodeSupport.LOCAL_OFFSET.id(), "geometry_node.transform.local_offset.help")
                        .input(AnchorNodeSupport.LOCAL_ROTATION.id(), "geometry_node.transform.local_rotation.help")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.TICK, "tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 绑定核心实体
                .addRow(AnchorNodeSupport.row(StandardPorts.SPATIAL_ANCHOR))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.STRING.toInput(), UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, new String[]{"fixed", "none", "third_person_left_hand", "third_person_right_hand", "first_person_left_hand", "first_person_right_hand", "head", "gui", "ground"},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.display.item_context.fixed",
                                        "geometry_node.display.item_context.none",
                                        "geometry_node.display.item_context.third_person_left_hand",
                                        "geometry_node.display.item_context.third_person_right_hand",
                                        "geometry_node.display.item_context.first_person_left_hand",
                                        "geometry_node.display.item_context.first_person_right_hand",
                                        "geometry_node.display.item_context.head",
                                        "geometry_node.display.item_context.gui",
                                        "geometry_node.display.item_context.ground"
                                }
                        ))
                // 核心三大动态矢量

                .addPassthroughInput(AnchorNodeSupport.WORLD_OFFSET, UIHint.VECTOR)
                .addPassthroughInput(AnchorNodeSupport.WORLD_ROTATION, UIHint.VECTOR)
                .addRow(PortRow.passthrough(AnchorNodeSupport.LOCAL_OFFSET, UIHint.VECTOR,null,null))
                .addRow(PortRow.passthrough(AnchorNodeSupport.LOCAL_ROTATION, UIHint.VECTOR,null,null))
                .addRow(PortRow.passthrough(SCALE_PORT, UIHint.VECTOR,null,null))
                .addPassthroughInput(StandardPorts.TICK.toInput(20), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        SpatialAnchor anchor = AnchorNodeSupport.capture(context, StandardPorts.SPATIAL_ANCHOR);
        if (anchor == null
                    || !VisualSpatialAnchors.canBroadcast(anchor, level)) return next(StandardPorts.FLOW_OUT.getId());

        ItemStack itemStack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        if (itemStack == null || itemStack.isEmpty()) itemStack = new ItemStack(Items.STONE);

        String displayContext = getInput(context, StandardPorts.STRING.getId(), String.class);
        if (displayContext == null) displayContext = "fixed";

        // 获取基础的死数值偏移
        Vec3 baseScale = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (baseScale == null) baseScale = new Vec3(1, 1, 1);

        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        if (duration == null || duration <= 0) duration = 20;

        Map<String, ExpressionData> expressions = new LinkedHashMap<>();
        putInputExpression(context, StandardPorts.SIZE_3.getId(), "scale", expressions);

        CompoundTag extraData = new CompoundTag();
        VisualSpatialAnchors.write(extraData, "anchor", anchor, level);
        putInputExpression(context, StandardPorts.SPATIAL_ANCHOR.getId(), "anchor", expressions);

        // 写入保底底数 (彻底移除了 StartX/Y/Z)
        VisualTransformInputs.write(context, extraData, expressions);
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
