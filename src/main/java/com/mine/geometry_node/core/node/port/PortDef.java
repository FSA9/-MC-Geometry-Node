package com.mine.geometry_node.core.node.port;

import net.minecraft.network.chat.Component;

/**
 * [元数据] 端口定义
 * 包含 UI 渲染所需的一切信息。
 */
public record PortDef(
        String id,               // 内部唯一标识 (JSON Key / Context Key)
        Component displayName,   // 显示名称 (支持多语言)
        PortType type,           // 数据类型
        Object defaultValue,     // 默认初始值 (允许覆盖 PortType 的默认值)
        boolean hidePin
) {

    public static PortDef create(String id, String nameKey, PortType type) {
        return new PortDef(id, Component.translatable(nameKey), type, type.getDefaultValue(), false);
    }

    public static PortDef create(String id, String nameKey, PortType type, Object defaultValue) {
        return new PortDef(id, Component.translatable(nameKey), type, defaultValue, false);
    }

    public static PortDef exec(String id, String nameKey) {
        return new PortDef(id, Component.translatable(nameKey), PortType.EXECUTION, null, false);
    }

    public PortDef hiddenPin() {
        return new PortDef(this.id, this.displayName, this.type, this.defaultValue, true);
    }

    public PortDef withHiddenPin(boolean hide) {
        return new PortDef(this.id, this.displayName, this.type, this.defaultValue, hide);
    }
}