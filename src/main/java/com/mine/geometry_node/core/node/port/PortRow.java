package com.mine.geometry_node.core.node.port;

import com.mine.geometry_node.core.node.meta.MetaKey;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import org.jetbrains.annotations.Nullable;
import java.util.Map;

public record PortRow(
        @Nullable PortDef leftPort,       // 左侧输入端口
        @Nullable PortDef rightPort,      // 右侧输出端口
        UIHint uiHint,                    // 内联控件类型
        @Nullable String customWidgetId,  // CUSTOM 组件 ID
        @Nullable Map<MetaKey<?>, Object> hintParams // 额外静态参数
) { }