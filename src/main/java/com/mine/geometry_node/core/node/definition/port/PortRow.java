package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.node.meta.MetaKey;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.Objects;

public record PortRow(
        @Nullable PortDef leftPort,       // 左侧输入端口
        @Nullable PortDef rightPort,      // 右侧输出端口
        UIHint uiHint,                    // 内联控件类型
        @Nullable String customWidgetId,  // CUSTOM 组件 ID
        @Nullable Map<MetaKey<?>, Object> hintParams, // 额外静态参数
        boolean dataPassthrough
) {
    public PortRow(@Nullable PortDef leftPort, @Nullable PortDef rightPort, UIHint uiHint,
                   @Nullable String customWidgetId,
                   @Nullable Map<MetaKey<?>, Object> hintParams) {
        this(leftPort, rightPort, uiHint, customWidgetId, hintParams, false);
    }

    public PortRow {
        if (dataPassthrough) {
            if (leftPort == null || rightPort == null
                    || leftPort.type().isFlow() || rightPort.type().isFlow()
                    || !Objects.equals(leftPort.id(), rightPort.id())
                    || leftPort.type() != rightPort.type()) {
                throw new IllegalArgumentException(
                        "A data passthrough row requires matching non-flow input and output ports");
            }
        }
    }

    public static PortRow passthrough(PortDef input, UIHint uiHint,
                                      @Nullable String customWidgetId,
                                      @Nullable Map<MetaKey<?>, Object> hintParams) {
        if (input == null || input.type().isFlow()) {
            throw new IllegalArgumentException("Only data inputs can define passthrough outputs");
        }
        PortDef output = new PortDef(
                input.id(), Component.empty(), input.type(), null,
                input.hidePin(), false
        );
        return new PortRow(input, output, uiHint, customWidgetId, hintParams, true);
    }
}
