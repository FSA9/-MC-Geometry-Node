package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnEntityTick extends BaseEventNode {

    public static final String TYPE_ID = "on_entity_tick";

    // 定义存储在 JSON properties 里的键名
    public static final String PROPERTY_INTERVAL = "interval";
    public static final String PROPERTY_OFFSET = "offset";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_entity_tick"))
                // 仅保留向外输出的端口，保持视觉上的“纯粹事件源”
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))

                // 【核心变化】纯静态 UI 控件，左右端口全部设为 null
                .addRow(new PortRow(
                        null, null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.BIND_PROPERTY, PROPERTY_INTERVAL)
                ))
                .addRow(new PortRow(
                        null, null, UIHint.INPUT, null,
                        Map.of(PortMetaKeys.BIND_PROPERTY, PROPERTY_OFFSET)
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 静态预检拦截网已经在 Java 底层生效。
        // 这里无条件放行，实现 0 计算开销。
        return next(StandardPorts.FLOW_OUT.getId());
    }
}