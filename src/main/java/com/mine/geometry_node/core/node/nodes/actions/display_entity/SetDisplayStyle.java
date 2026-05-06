package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class SetDisplayStyle extends BaseNode {

    public static final String TYPE_ID = "set_display_style";
    public static final String PROPERTY_BILLBOARD = "billboard_mode";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_display_style"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 朝向与亮度
                .addRow(new PortRow(
                        StandardPorts.BILLBOARD.toInput(), null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_BILLBOARD,
                                PortMetaKeys.OPTIONS, new String[]{"fixed", "vertical", "horizontal", "center"}
                        )
                ))
                .addRow(new PortRow(StandardPorts.BRIGHTNESS.toInput(-1), null, UIHint.INPUT, null, null)) // -1 代表跟随环境光
                // 阴影系统
                .addRow(new PortRow(StandardPorts.SHADOW_RADIUS.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SHADOW_STRENGTH.toInput(1.0f), null, UIHint.INPUT, null, null))
                // 渲染剔除与视距
                .addRow(new PortRow(StandardPorts.VIEW_RANGE.toInput(1.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.WIDTH.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.HEIGHT.toInput(0.0f), null, UIHint.INPUT, null, null))
                // 发光轮廓颜色
                .addRow(new PortRow(StandardPorts.GLOW_COLOR.toInput(-1), null, UIHint.INPUT, null, null)) // -1 代表无强制覆写
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        String billboard = context.getConfig(PROPERTY_BILLBOARD, String.class, "fixed");
        Integer brightness = getInput(context, StandardPorts.BRIGHTNESS.getId(), Integer.class);
        Float shadowRadius = getInput(context, StandardPorts.SHADOW_RADIUS.getId(), Float.class);
        Float shadowStrength = getInput(context, StandardPorts.SHADOW_STRENGTH.getId(), Float.class);
        Float viewRange = getInput(context, StandardPorts.VIEW_RANGE.getId(), Float.class);

        // 新增参数获取
        Float width = getInput(context, StandardPorts.WIDTH.getId(), Float.class);
        Float height = getInput(context, StandardPorts.HEIGHT.getId(), Float.class);
        Integer glowColor = getInput(context, StandardPorts.GLOW_COLOR.getId(), Integer.class);

        for (Entity entity : entities) {
            if (entity instanceof Display displayEntity) {
                CompoundTag nbt = new CompoundTag();
                displayEntity.saveWithoutId(nbt);

                nbt.putString("billboard", billboard);
                if (shadowRadius != null) nbt.putFloat("shadow_radius", shadowRadius);
                if (shadowStrength != null) nbt.putFloat("shadow_strength", shadowStrength);
                if (viewRange != null) nbt.putFloat("view_range", viewRange);

                // 写入剔除盒子大小 (大于0才生效)
                if (width != null && width > 0) nbt.putFloat("width", width);
                if (height != null && height > 0) nbt.putFloat("height", height);

                // 写入发光颜色 (-1 表示清除覆写，跟随队伍颜色)
                if (glowColor != null && glowColor != -1) {
                    nbt.putInt("glow_color_override", glowColor);
                } else {
                    nbt.remove("glow_color_override");
                }

                // 发光亮度覆写处理
                if (brightness != null && brightness >= 0) {
                    int b = Math.min(15, brightness);
                    CompoundTag lightTag = new CompoundTag();
                    lightTag.putInt("sky", b);
                    lightTag.putInt("block", b);
                    nbt.put("brightness", lightTag);
                } else {
                    nbt.remove("brightness");
                }

                displayEntity.load(nbt);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}