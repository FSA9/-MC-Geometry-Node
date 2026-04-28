package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.execution.GraphProcess;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class FinishGraph extends BaseNode {

    public static final String TYPE_ID = "finish_graph";
    public static final String PROPERTY_SELECTED = PortMetaKeys.DYNAMIC_REGISTRY_ID.id();

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.finish_graph"))
                // 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 第一行：作用域（使用维度下拉框逻辑，Key 必须全部为 MetaKey）
                .addRow(new PortRow(
                        null,
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.BIND_PROPERTY, PROPERTY_SELECTED,
                                PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:dimension"
                        )
                ))
                // 第二行：实体
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                // 第三行：名称 (图 ID，填写时不带 .json)
                .addRow(new PortRow(StandardPorts.NAME.toInput(""), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        String targetGraphId = getInput(context, StandardPorts.NAME.getId(), String.class);
        String scope = (String) context.getNodeProperty(PROPERTY_SELECTED);
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        if (targetGraphId == null || targetGraphId.isEmpty()) return next(StandardPorts.FLOW_OUT.getId());

        // 鲁棒性：自动修剪用户误填的 .json 后缀
        if (targetGraphId.endsWith(".json")) {
            targetGraphId = targetGraphId.substring(0, targetGraphId.length() - 5);
        }

        // 模式 A：如果连接了实体，优先结束实体上的进程
        if (!entities.isEmpty()) {
            for (Entity target : entities) {
                EntityGraphAttachment attachment = target.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
                if (attachment != null) {
                    terminateMatchingProcesses(attachment.getProcesses(), targetGraphId);
                }
            }
        }
        // 模式 B：根据作用域结束环境进程
        else {
            ServerLevel currentLevel = context.getLevel();
            if (currentLevel == null) return next(StandardPorts.FLOW_OUT.getId());

            if ("global".equals(scope) || scope == null) {
                // 默认全局通常在主世界容器中
                terminateMatchingProcesses(LevelGraphAttachment.get(currentLevel.getServer().overworld()).getProcesses(), targetGraphId);
            } else {
                // 按具体维度 ID 结束
                ResourceLocation dimLoc = ResourceLocation.tryParse(scope);
                if (dimLoc != null) {
                    ServerLevel targetLevel = currentLevel.getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimLoc));
                    if (targetLevel != null) {
                        terminateMatchingProcesses(LevelGraphAttachment.get(targetLevel).getProcesses(), targetGraphId);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    private void terminateMatchingProcesses(Iterable<GraphProcess> processes, String targetGraphId) {
        if (processes == null) return;
        for (GraphProcess process : processes) {
            if (!process.isFinished() && targetGraphId.equals(process.getGraphId())) {
                process.terminate();
            }
        }
    }
}