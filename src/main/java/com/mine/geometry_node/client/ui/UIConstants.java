package com.mine.geometry_node.client.ui;

/**
 * UI 常量配置中心
 * 包含基础调色板定义和各模块的具体参数配置
 */
public class UIConstants {

    // ==========================================
    // 基础调色板 (Color Palette - 集中定义所有原始颜色值)
    // ==========================================

    // --- 背景深色系 ---
    public static final int CLR_BG_DARK_1 = 0xFF181818;      // 视口背景 (最深)
    public static final int CLR_BG_DARK_2 = 0xFF1D1D1D;      // 根容器与菜单背景
    public static final int CLR_BG_DARK_3 = 0xFF252525;      // 底部面板背景
    public static final int CLR_BG_DARK_4 = 0xFF2D2D2D;      // 顶部标题栏背景
    public static final int CLR_BG_DARK_5 = 0xFF303030;      // 左右侧属性栏面板背景
    public static final int CLR_BG_NODE_BODY = 0xE6303030;   // 节点主体背景 (略透)

    // --- 装饰与功能色 ---
    public static final int CLR_BLACK = 0xFF000000;          // 纯黑 (基础描边/坐标轴)
    public static final int CLR_NODE_OUTLINE = 0xFF111111;   // 节点未选中时的外框边框色
    public static final int CLR_WHITE = 0xFFFFFFFF;          // 纯白 (高亮/标题文字/选中态边框)
    public static final int CLR_GRAY_TEXT = 0xFFAAAAAA;      // 辅助文字灰色
    public static final int CLR_GRAY_LABEL = 0xFFCCCCCC;     // 节点内端口标签文字灰色
    public static final int CLR_SEARCH_BG = 0xFF333333;      // 搜索框深灰背景
    public static final int CLR_GRID_LINE = 0xFF282828;      // 画布网格线暗灰色
    public static final int CLR_HOVER_WHITE = 0x40FFFFFF;    // 悬停覆盖色 (半透白)

    // --- 框选与节点类型色 ---
    public static final int CLR_SELECT_FILL = 0x3342A5F5;    // 框选区域填充蓝 (半透)
    public static final int CLR_SELECT_STROKE = 0xFF42A5F5;  // 框选区域边框蓝

    // --- 屏幕显示 ---
    public static final float mDensity = 2.0f;               // 屏幕像素密度

    // ==========================================
    // 基础尺寸模数 (Base Module System)
    // ==========================================
    public static final int GRID_SIZE = 15;                  // 基础网格模数大小 (px)

    // ==========================================
    // 模块特定配置 (Module Configurations)
    // ==========================================

    /**
     * MainUI 主界面布局配置
     */
    public static class MainUI {
        public static final int BG_ROOT = CLR_BG_DARK_2;         // 根布局背景
        public static final int BG_HEADER = CLR_BG_DARK_4;       // 顶部标题栏背景
        public static final int BG_OUTLINER = CLR_BG_DARK_5;     // 左侧大纲面板背景
        public static final int BG_VIEWPORT = CLR_BG_DARK_1;     // 中间视口背景
        public static final int BG_PROPERTIES = CLR_BG_DARK_5;   // 右侧属性面板背景
        public static final int BG_TIMELINE = CLR_BG_DARK_3;     // 底部时间轴面板背景
        public static final int BG_SPLITTER = CLR_BLACK;         // 拖拽分割线颜色
        public static final int TEXT_COLOR = CLR_GRAY_TEXT;      // 默认全局文字颜色

        public static final int HEIGHT_HEADER = 30;              // 顶部栏高度 (dp)
        public static final int HEIGHT_BOTTOM_DEFAULT = 150;     // 底部栏默认高度 (dp)
        public static final int HEIGHT_BOTTOM_MIN = 50;          // 底部栏最小高度 (dp)
        public static final int SPLITTER_HITBOX_SIZE = 2;        // 分割线触摸触发区粗细 (dp)
        public static final int SPLITTER_VISUAL_SIZE = 2;        // 分割线视觉粗细 (dp)
        public static final int TEXT_SIZE = 14;                  // 全局默认字体大小 (sp)

        public static final float WEIGHT_LEFT = 0.2f;            // 左侧面板初始宽度权重
        public static final float WEIGHT_CENTER = 0.6f;          // 中间视口初始宽度权重
        public static final float WEIGHT_RIGHT = 0.2f;           // 右侧面板初始宽度权重
        public static final float WEIGHT_MIN = 0.05f;            // 面板可缩放的最小权重边界
    }

    /**
     * Node 节点内部布局配置 (基于模数推导)
     */
    public static class Node {
        public static final int NODE_WIDTH = 12 * GRID_SIZE;                      // 节点总宽度
        public static final int ROW_HEIGHT = 2 * GRID_SIZE;                      // 节点单行高度
        public static final int HEADER_HEIGHT = 2 * GRID_SIZE;                   // 节点标题栏高度

        public static final float PORT_VISUAL_RADIUS = ROW_HEIGHT / 4.0f;        // 端口视觉半径
        public static final float PORT_HITBOX_RADIUS = PORT_VISUAL_RADIUS * 1.2f; // 端口交互判定半径

        public static final float CORNER_RADIUS = 6.0f;                          // 节点圆角半径
        public static final float STROKE_WIDTH_NORMAL = 1.5f;                    // 节点普通状态边框线宽
        public static final float STROKE_WIDTH_SELECTED = 2.5f;                  // 节点选中状态边框线宽

        public static final int LABEL_MARGIN_PORT = 8;                           // 端口与标签文本之间的间距
        public static final int TEXT_SIZE_HEADER = 10;                           // 节点标题字体大小
        public static final int TEXT_SIZE_LABEL = 10;                            // 节点内端口标签字体大小
    }

    /**
     * ViewPort 视口与网格配置
     */
    public static class ViewPort {
        public static final int BG_COLOR = CLR_BG_DARK_1;        // 视口画布背景色
        public static final int COLOR_GRID_LINE = CLR_GRID_LINE; // 网格线颜色
        public static final int COLOR_GRID_AXIS = CLR_BLACK;     // 中心坐标轴颜色

        public static final float LINE_WIDTH_NORMAL = 0.8f;      // 常规网格线宽
        public static final float LINE_WIDTH_AXIS = 1.0f;        // 中心坐标轴线宽
        public static final float LINE_WIDTH_CONNECTION = 3.0f;  // 节点连线的基础宽度

        public static final float ZOOM_MIN = 0.4f;               // 画布最小缩放倍率
        public static final float ZOOM_MAX = 10.0f;              // 画布最大缩放倍率
        public static final float ZOOM_SENSITIVITY = 0.1f;       // 鼠标滚轮缩放灵敏度步长

        /** 视口右键菜单配置 */
        public static class NodeMenu {
            public static final int BG_COLOR = CLR_BG_DARK_2;       // 菜单整体背景色
            public static final int SEARCH_BG_COLOR = CLR_SEARCH_BG; // 顶部搜索框背景色
            public static final int TEXT_COLOR = CLR_GRAY_TEXT;     // 菜单项默认文字颜色
            public static final int TEXT_COLOR_HOVER = CLR_WHITE;   // 菜单项悬停文字颜色
            public static final int TEXT_COLOR_SEARCH = CLR_WHITE;  // 搜索框输入文字颜色
            public static final int HOVER_COLOR = CLR_HOVER_WHITE;  // 菜单项悬停背景覆盖色

            public static final int HEIGHT_SEARCH_BOX = 36;         // 搜索框高度 (dp)
            public static final int ITEM_HEIGHT = 30;               // 单个菜单项高度 (dp)
            public static final int ITEM_WEIGHT = 180;              // 菜单整体宽度 (dp)
            public static final int BORDER_RADIUS = 0;              // 菜单边缘圆角 (dp)
            public static final double TEXT_SIZE = 0.5;             // 菜单项字体比例系数
        }
    }
}