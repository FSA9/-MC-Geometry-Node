package com.mine.geometry_node.core.node.nodes.events.player;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class OnPlayerKeyEvent extends BaseNode {

    public static final String TYPE_ID = "on_player_key_event";

    // 静态定义可选的按键和动作（严格限制玩家输入）
    public static final String[] VALID_KEYS = {
            "skill_1", "skill_2", "skill_3", "skill_4", "skill_5",
            "skill_6", "skill_7", "skill_8", "skill_9", "skill_10",
            "ctrl", "shift", "alt"
    };
    public static final String[] VALID_ACTIONS = {"PRESS", "RELEASE", "DOUBLE_CLICK"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_player_key_event"))
                // --- 输出 ---
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.TIME.toOutput(), UIHint.DEFAULT, null, null))

                // --- 输入 (通过 UIHint.SELECT 和 PortMetaKeys 限制为下拉框) ---
                .addRow(new PortRow(
                        StandardPorts.NAME.toInput("skill_1"), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, VALID_KEYS) // 注入按键选项
                ))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput("PRESS"), null,
                        UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, VALID_ACTIONS) // 注入动作选项
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 1. 获取系统真实派发的事件参数（来自第三阶段的 PlayerInputStateManager）
        String actualKeyId = (String) context.getEventData("key_id");
        String actualAction = (String) context.getEventData("action");

        // 2. 获取玩家在节点界面上填写的期望参数
        String expectedKeyId = getInput(context, StandardPorts.NAME.getId(), String.class);
        String expectedAction = getInput(context, StandardPorts.TYPE.getId(), String.class);

        // 3. 【核心比对】：如果按键或者动作类型对不上，直接静默销毁执行流
        if (actualKeyId == null || !actualKeyId.equals(expectedKeyId)) {
            return finish();
        }
        if (actualAction == null || !actualAction.equals(expectedAction)) {
            return finish();
        }

        // 4. 匹配成功！准备向下游输出数据
        Float duration = (Float) context.getEventData("duration");
        context.setTempData(StandardPorts.TIME.getId(), duration != null ? duration : 0.0f);

        // 实体数据 (Trigger Entity) 在 dispatchEvent 时就已经注好了，我们转存到 tempData 供下游连线使用
        Object entity = context.getEventData(StandardPorts.ENTITY.getId());
        if (entity != null) {
            context.setTempData(StandardPorts.ENTITY.getId(), entity);
        }

        // 5. 放行！
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 让下游的动作节点可以读取到我们刚才存入的实体和时长
        return context.getTempData(portName);
    }
}
