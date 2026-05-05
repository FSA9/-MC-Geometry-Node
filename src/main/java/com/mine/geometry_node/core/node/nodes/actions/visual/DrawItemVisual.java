package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrawItemVisual extends BaseNode {

    public static final String TYPE_ID = "draw_item_visual";
    public static final String PROPERTY_DISPLAY_CONTEXT = "item_display_context";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_item_visual"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 绑定核心实体 (有它 = 相对坐标，无它 = 绝对坐标)
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))

                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        null, null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_DISPLAY_CONTEXT,
                                PortMetaKeys.OPTIONS, new String[]{"fixed", "none", "third_person_left_hand", "third_person_right_hand", "first_person_left_hand", "first_person_right_hand", "head", "gui", "ground"}
                        )
                ))

                // 核心三大动态矢量（注意：START_POS 已被彻底删除）
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))

                .addRow(new PortRow(StandardPorts.TICK.toInput(20), null, UIHint.INPUT, null, null))
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

        String displayContext = context.getConfig(PROPERTY_DISPLAY_CONTEXT, String.class, "fixed");

        // 获取基础的死数值偏移
        Vec3 baseTrans = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Vec3 baseRot = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        Vec3 baseScale = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (baseTrans == null) baseTrans = Vec3.ZERO;
        if (baseRot == null) baseRot = Vec3.ZERO;
        if (baseScale == null) baseScale = new Vec3(1, 1, 1);

        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        if (duration == null || duration <= 0) duration = 20;

        // 【解决发包截断问题的核心思路】
        // 计算出一个绝对的“物理中心点”，你可以把这个 center 传给你的 broadcast 方法（如果你的底层支持传参的话）
        // 这样服务端就不会傻傻地在 0,0,0 发包了！
        Vec3 broadcastCenter = (sourceEntity != null) ? sourceEntity.position().add(baseTrans) : baseTrans;

        Map<String, String> expressions = new HashMap<>();
        Map<String, String> bindings = new HashMap<>();

        ExpressionData transExpr = getInput(context, StandardPorts.TRANSLATION.getId(), ExpressionData.class);
        extractVec3(transExpr, "trans", expressions, bindings);

        ExpressionData rotExpr = getInput(context, StandardPorts.ROTATION.getId(), ExpressionData.class);
        extractVec3(rotExpr, "rot", expressions, bindings);

        ExpressionData scaleExpr = getInput(context, StandardPorts.SIZE_3.getId(), ExpressionData.class);
        extractVec3(scaleExpr, "scale", expressions, bindings);

        CompoundTag extraData = new CompoundTag();
        extraData.putInt("sourceId", sourceId);

        // 写入保底底数 (彻底移除了 StartX/Y/Z)
        extraData.putDouble("bTransX", baseTrans.x); extraData.putDouble("bTransY", baseTrans.y); extraData.putDouble("bTransZ", baseTrans.z);
        extraData.putDouble("bRotX", baseRot.x); extraData.putDouble("bRotY", baseRot.y); extraData.putDouble("bRotZ", baseRot.z);
        extraData.putDouble("bScaleX", baseScale.x); extraData.putDouble("bScaleY", baseScale.y); extraData.putDouble("bScaleZ", baseScale.z);

        extraData.putString("item_display", displayContext);

        // 1.21 物品序列化
        net.minecraft.nbt.Tag itemTag = itemStack.saveOptional(level.registryAccess());
        extraData.put("item", itemTag);

        // 注意：如果你有办法修改 broadcastDynamicVisual 的方法签名，请把上面的 broadcastCenter 传进去作为发包中心！
        context.broadcastDynamicVisual("item_visual", 0xFFFFFFFF, duration, expressions, bindings, extraData);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    protected void extractVec3(ExpressionData expr, String prefix, Map<String, String> expressions, Map<String, String> bindings) {
        if (expr == null || expr.formula() == null || expr.formula().isEmpty()) return;

        String f = expr.formula().trim();
        if (f.startsWith("vec3(") && f.endsWith(")")) {
            String inner = f.substring(5, f.length() - 1);
            List<String> parts = new ArrayList<>();
            int bracketLevel = 0;
            StringBuilder currentStr = new StringBuilder();

            for (char c : inner.toCharArray()) {
                if (c == '(') bracketLevel++;
                else if (c == ')') bracketLevel--;
                else if (c == ',' && bracketLevel == 0) {
                    parts.add(currentStr.toString().trim());
                    currentStr.setLength(0);
                    continue;
                }
                currentStr.append(c);
            }
            parts.add(currentStr.toString().trim());

            if (parts.size() >= 3) {
                if (!parts.get(0).equals("0")) expressions.put(prefix + "X", parts.get(0));
                if (!parts.get(1).equals("0")) expressions.put(prefix + "Y", parts.get(1));
                if (!parts.get(2).equals("0")) expressions.put(prefix + "Z", parts.get(2));
            }
        }
        if (expr.bindings() != null) bindings.putAll(expr.bindings());
    }
}