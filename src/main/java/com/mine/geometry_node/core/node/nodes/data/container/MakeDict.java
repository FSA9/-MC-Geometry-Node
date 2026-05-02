package com.mine.geometry_node.core.node.nodes.data.container;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class MakeDict extends BaseNode {

    public static final String TYPE_ID = "make_dict";

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        int portCount = 1;
        if (instanceData != null && instanceData.properties.containsKey(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id())) {
            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                portCount = Math.max(1, n.intValue());
            } else if (countObj instanceof String s) {
                try { portCount = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        portCount = Math.max(1, Math.min(portCount, 30));
        return buildDef(portCount);
    }

    private NodeDef buildDef(int portCount) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.make_dict"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);

        // 1. 输出端口
        builder.addRow(new PortRow(null, StandardPorts.DICT.toOutput(), UIHint.DEFAULT, null, null));

        // 2. 基础字典输入 (用于继承和覆盖已有字典)
        builder.addRow(new PortRow(StandardPorts.DICT.toInput(), null, UIHint.DEFAULT, null, null));

        // 3. 动态生成键值对双行输入
        for (int i = 1; i <= portCount; i++) {
            // ---- 第一行：Key (STRING类型) ----
            PortDef keyPort = new PortDef("dict_key_" + i, Component.literal("Key " + i), PortType.STRING, "");
            builder.addRow(new PortRow(
                    keyPort,
                    null,
                    UIHint.INPUT, // 允许玩家在没连线时直接敲字符串
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true,
                            PortMetaKeys.DYNAMIC_INDEX, i
                    )
            ));

            // ---- 第二行：Value (ANY类型) ----
            PortDef valPort = new PortDef("dict_value_" + i, Component.literal("Value " + i), PortType.ANY, null);
            builder.addRow(new PortRow(
                    valPort,
                    null,
                    UIHint.DEFAULT,
                    null,
                    Map.of(
                            PortMetaKeys.IS_DYNAMIC, true
                    )
            ));
        }

        return builder.build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.DICT.getId().equals(portName)) return null;

        Map<String, Object> resultDict = new HashMap<>();

        // 1. 先吸收基础字典的数据（如果连入了基础字典的话）
        Map<String, Object> baseDict = getInputDict(context, StandardPorts.DICT.getId());
        if (baseDict != null && !baseDict.isEmpty()) {
            resultDict.putAll(baseDict);
        }

        // 2. 获取动态条目数量
        int portCount = 1;
        Object countObj = context.getNodeProperty(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) portCount = Math.max(1, n.intValue());

        // 3. 逐个覆盖或新增动态键值对
        for (int i = 1; i <= portCount; i++) {
            String key = getInput(context, "dict_key_" + i, String.class);

            Object value = getRawInput(context, "dict_value_" + i);

            // 只有当 Key 有效且 Value 连了线，才写入字典
            if (key != null && !key.trim().isEmpty() && value != null) {
                resultDict.put(key.trim(), value);
            }
        }

        return resultDict;
    }
}