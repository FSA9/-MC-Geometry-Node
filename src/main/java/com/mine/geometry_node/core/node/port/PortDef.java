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
        Object defaultValue      // 默认初始值 (允许覆盖 PortType 的默认值)
) {

    // --- 快捷构造工厂 ---

    /**
     * 创建普通端口 (自动转换翻译 Key，使用类型的默认值)
     */
    public static PortDef create(String id, String nameKey, PortType type) {
        return new PortDef(id, Component.translatable(nameKey), type, type.getDefaultValue());
    }

    /**
     * 创建带自定义默认值的端口 (用于覆盖 PortType 的默认值，如 "Hello World")
     */
    public static PortDef create(String id, String nameKey, PortType type, Object defaultValue) {
        return new PortDef(id, Component.translatable(nameKey), type, defaultValue);
    }

    /**
     * 创建执行流端口
     */
    public static PortDef exec(String id, String nameKey) {
        return new PortDef(id, Component.translatable(nameKey), PortType.EXECUTION, null);
    }
}