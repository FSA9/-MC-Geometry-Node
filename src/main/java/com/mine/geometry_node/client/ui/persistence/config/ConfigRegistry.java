package com.mine.geometry_node.client.ui.persistence.config;

import java.util.List;

public final class ConfigRegistry {
    private static final List<ConfigDefinition> ENTRIES = List.of(
            ConfigDefinition.integer("viewport.gridSize", "网格大小", "节点和图框吸附、背景网格绘制使用的基础尺寸。", 1, 500, 1),
            ConfigDefinition.bool("viewport.snapToGrid", "吸附到网格", "开启后移动节点和图框时对齐到网格。"),
            ConfigDefinition.bool("viewport.showGridAndAxis", "显示栅格与坐标轴", "控制 viewport 背景栅格和坐标轴是否显示。"),
            ConfigDefinition.floating("node.cornerRadius", "节点圆角", "节点、输入框和选择提示使用的圆角尺寸。", 0, 24, 0.5),
            ConfigDefinition.pathList("assetBrowser.quickAccessPaths", "快捷访问路径", "资产浏览器左侧快捷访问目录列表。"),
            ConfigDefinition.choice("assetBrowser.viewMode", "资产显示模式", "资产浏览器文件列表的显示方式。",
                    List.of("LIST", "ICON_SMALL", "ICON_MEDIUM", "ICON_LARGE")),
            ConfigDefinition.keyBinding("keyBindings.global.undo", "撤销", "撤销上一次操作。"),
            ConfigDefinition.keyBinding("keyBindings.global.redo", "重做", "恢复上一次撤销的操作。"),
            ConfigDefinition.keyBinding("keyBindings.global.save", "保存", "保存当前打开的蓝图。"),
            ConfigDefinition.keyBinding("keyBindings.global.copy", "复制", "复制当前作用域选中的内容。"),
            ConfigDefinition.keyBinding("keyBindings.global.paste", "粘贴", "在当前作用域粘贴剪贴板内容。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.delete", "删除", "删除 viewport 当前选中的节点或图框。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.toggleSnapToGrid", "切换吸附", "开启或关闭 viewport 网格吸附。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.toggleGridAndAxis", "切换栅格与坐标轴", "显示或隐藏 viewport 背景栅格和坐标轴。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.toggleRightSidebar", "切换右侧栏", "显示或隐藏图属性侧栏。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.moveSelection", "移动", "让 viewport 当前选中节点跟随鼠标移动。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.groupIntoFrame", "并入图框", "把 viewport 当前选中的节点并入新图框。"),
            ConfigDefinition.keyBinding("keyBindings.viewport.groupIntoNodeGroup", "合并为图组", "把 viewport 当前选中的节点合并为新图组。"),
            ConfigDefinition.shortcut("keyBindings.shopEditor.clearSlot", "清空商品槽", "清空商店编辑器中的商品槽。")
    );

    private ConfigRegistry() {
    }

    public static List<ConfigDefinition> entries() {
        return ENTRIES;
    }
}
