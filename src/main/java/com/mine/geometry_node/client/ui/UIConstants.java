package com.mine.geometry_node.client.ui;

/**
 * UI 常量配置中心
 * <p>
 * 架构职责：集中管理全局基础调色板、各组件视觉尺寸以及交互阈值判定参数。
 * 避免在逻辑代码中出现“魔法数字”(Magic Numbers)。
 */
public class UIConstants {

    // ==========================================
    // 基础调色板 (Color Palette)
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
    public static final int CLR_GRID_LINE = 0xFF282828;      // 画布网格线暗灰色

    // --- 屏幕显示 ---
    public static float mDensity = 2.0f;               // 屏幕像素密度

    // ==========================================
    // 基础尺寸模数 (Base Module System)
    // ==========================================
    public static int GRID_SIZE = 15;                  // 基础网格模数大小 (px)

    /**
     * MainUI 主界面布局配置
     */
    public static class MainUI {
        public static final int BG_ROOT = CLR_BG_DARK_2;         // 根布局背景
        public static final int BG_TIMELINE = CLR_BG_DARK_3;     // 底部时间轴面板背景
        public static final int TEXT_COLOR = CLR_GRAY_TEXT;      // 默认全局文字颜色

        public static final int HEIGHT_HEADER = 30;              // 顶部栏高度 (dp)
        public static final int TEXT_SIZE = 14;                  // 全局默认字体大小 (sp)

        public static final float WEIGHT_CENTER = 0.6f;          // 中间视口初始宽度权重
        public static final float WEIGHT_RIGHT = 0.2f;           // 右侧面板初始宽度权重
        public static final float WEIGHT_MIN = 0.05f;            // 面板可缩放的最小权重边界
    }

    /**
     * Node 节点内部布局配置
     */
    public static class Node {
        public static final int NODE_WIDTH = 6 * GRID_SIZE;                      // 节点总宽度
        public static final int ROW_HEIGHT = GRID_SIZE;                       // 节点单行高度
        public static final int HEADER_HEIGHT = GRID_SIZE;                    // 节点标题栏高度

        public static final float STROKE_WIDTH_NORMAL = 1.5f;                     // 节点普通状态边框线宽
        public static final float STROKE_WIDTH_SELECTED = 2.5f;                   // 节点选中状态边框线宽

        // --- 文本标签与复选框排版参数 ---
        public static final int LABEL_MARGIN_PORT = 5;                           // 端口与标签文本之间的基础间距
        public static final int MARGIN_CHECKBOX_GAP = 3;                          // 复选框与文本标签间的间距
        public static final int CHECKBOX_DEFAULT_WIDTH = 13;                     // 复选框控件的默认宽度
        public static final int TEXT_SIZE_HEADER = 9;                            // 节点标题字体大小
        public static final int TEXT_SIZE_LABEL = 9;                             // 节点内端口标签字体大小

        // --- 端口尺寸与判定参数 ---
        public static final float PORT_VISUAL_RADIUS = ROW_HEIGHT / 4.0f;         // 端口视觉圆点半径
        public static final float PORT_HITBOX_RADIUS_RATIO = 1.1f;                // 交互判定半径相对于视觉半径的倍数
        public static final float PORT_HITBOX_RADIUS = PORT_VISUAL_RADIUS * PORT_HITBOX_RADIUS_RATIO; // 端口交互判定半径

        public static final int CLR_DYNAMIC_BTN_BG = 0xFF35373B;                  // 动态按钮背景色
        public static final int CLR_DYNAMIC_BTN_STROKE = 0xFF5A5E66;              // 动态按钮描边色
        public static final int CLR_DYNAMIC_BTN_FG = 0xFFE6EAF0;                  // 动态按钮前景色
    }

    /**
     * ViewPort 视口与画布配置
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

        // --- 画布框选样式配置 ---
        public static class Selection {
            public static final int CLR_FILL = 0x3344AAFF;       // 框选区域填充蓝 (半透)
            public static final int CLR_BORDER = 0xFF44AAFF;     // 框选区域边框蓝
            public static final float STROKE_WIDTH = 1.0f;       // 框选边框线宽
        }

        // --- 节点连线样式配置 ---
        public static class Connection {
            public static final int CLR_DRAFT_LINE = 0xFFE0E0E0; // 交互草稿连线颜色
            public static final float LINE_WIDTH_DRAFT = 3.0f;       // 正在拖拽的草稿连线宽度
        }

        // --- 画布交互阈值配置 ---
        public static class Interaction {
            public static final float TOUCH_SLOP = 5.0f;         // 物理屏幕防抖阈值 (超过此值判定为拖拽)
            public static final float MIN_DRAG_DISTANCE = 0.1f;  // 逻辑坐标防抖阈值 (微小浮点误差过滤)
        }

        /** 视口右键菜单配置 */
        public static class NodeMenu {
            public static final int TEXT_COLOR_HOVER = CLR_WHITE;   // 菜单项悬停文字颜色
            public static final int TEXT_COLOR_SEARCH = CLR_WHITE;  // 搜索框输入文字颜色
            public static final int BORDER_RADIUS = 1;              // 菜单边缘圆角 (dp)
        }
    }
}
