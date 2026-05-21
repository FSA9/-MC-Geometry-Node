package com.mine.geometry_node.client.ui.viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.screen.PlayerInventoryPickerScreen;
import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.utils.ItemCodecUtils;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.mc.MinecraftSurfaceView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import javax.annotation.Nonnull;

public class UIItemSlot extends FrameLayout {
    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;
    private final Paint mPaint = new Paint();

    private ItemStack mCachedStack = ItemStack.EMPTY;
    private String mLastJson = null;
    private long mLastClickTime = 0;

    public UIItemSlot(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mPortId = portId;
        this.mEditorContext = editorContext;

        setWillNotDraw(false);
        updateCache();

        MinecraftSurfaceView surfaceView = new MinecraftSurfaceView(context);
        surfaceView.setRenderer(new MinecraftSurfaceView.Renderer() {
            @Override
            public void onSurfaceChanged(int width, int height) {}

            @Override
            public void onDraw(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick, double guiScale, float alpha) {
                if (!mCachedStack.isEmpty()) {
                    gr.pose().pushPose();

                    float globalScale = 1.0f;
                    icyllis.modernui.view.View current = UIItemSlot.this;
                    while (current != null) {
                        globalScale *= current.getScaleX();
                        icyllis.modernui.view.ViewParent parent = current.getParent();
                        if (parent instanceof icyllis.modernui.view.View) {
                            current = (icyllis.modernui.view.View) parent;
                        } else {
                            current = null;
                        }
                    }

                    // 2. 将原版固定的 16x16 渲染比例缩放到与蓝图一致，彻底解决被裁剪的问题！
                    gr.pose().scale(globalScale, globalScale, 1.0f);

                    // 3. 计算居中偏移 (逻辑大小减去 16 除以 2)
                    float logicW = UIItemSlot.this.getWidth();
                    float logicH = UIItemSlot.this.getHeight();
                    float offsetX = (logicW - 16f) / 2f;
                    float offsetY = (logicH - 16f) / 2f;

                    // 4. 渲染物品及其耐久条、角标
                    gr.renderItem(mCachedStack, (int) offsetX, (int) offsetY);
                    gr.renderItemDecorations(Minecraft.getInstance().font, mCachedStack, (int) offsetX, (int) offsetY);

                    gr.pose().popPose();
                }
            }
        });

        // 撑满容器
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(surfaceView, lp);
    }

    private void updateCache() {
        Object rawVal = mNodeData.inputs.get(mPortId);
        String json = rawVal instanceof String ? (String) rawVal : "";
        if (!json.equals(mLastJson)) {
            mLastJson = json;
            if (Minecraft.getInstance().level != null) {
                mCachedStack = ItemCodecUtils.fromJson(json, Minecraft.getInstance().level.registryAccess());
            } else {
                mCachedStack = ItemStack.EMPTY;
            }
        }
    }

    // 绘制灰色凹陷背景
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateCache();

        float w = getWidth();
        float h = getHeight();
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFF8B8B8B);
        canvas.drawRect(0, 0, w, h, mPaint);
        mPaint.setColor(0xFF373737);
        canvas.drawRect(0, 0, w, 2, mPaint);
        canvas.drawRect(0, 0, 2, h, mPaint);
        mPaint.setColor(0xFFFFFFFF);
        canvas.drawRect(w - 2, 0, w, h, mPaint);
        canvas.drawRect(0, h - 2, w, h, mPaint);
        mPaint.setColor(0xFF8B8B8B);
        canvas.drawRect(2, 2, w - 2, h - 2, mPaint);
    }

    // 【关键修复】鼠标悬浮事件，将坐标送给全局 Tooltip 代理
    @Override
    public boolean onHoverEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {

            // 获取控件在屏幕上的物理坐标
            int[] loc = new int[2];
            getLocationOnScreen(loc);

            // 转换为 Minecraft 的 GUI 坐标，并向右下角稍作偏移，防止遮挡鼠标
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            int guiX = (int) (loc[0] / guiScale) + 12;
            int guiY = (int) (loc[1] / guiScale) + 12;

            ItemTooltipProxy.setTooltipTask(mCachedStack, guiX, guiY);

        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            ItemTooltipProxy.clearTooltipTask(mCachedStack);
        }
        return super.onHoverEvent(event);
    }

    // 防止节点被删除时产生幽灵 Tooltip
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ItemTooltipProxy.clearTooltipTask(mCachedStack);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - mLastClickTime < 300) {
                onDoubleClick();
            }
            mLastClickTime = currentTime;
            return true;
        }
        return true;
    }

    private void onDoubleClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        net.minecraft.client.gui.screens.Screen currentModernUIScreen = mc.screen;

        mc.tell(() -> {
            mc.setScreen(new PlayerInventoryPickerScreen(currentModernUIScreen, pickedStack -> {
                if (mEditorContext != null) {
                    String newJson = ItemCodecUtils.toJson(pickedStack, mc.level.registryAccess());
                    Object oldVal = mNodeData.inputs.get(mPortId);
                    mEditorContext.getCommandManager().execute(
                            new CmdChangeInputValue(mEditorContext.getGraphController(), mNodeData.id, mPortId, oldVal, newJson)
                    );
                    updateCache();
                    this.invalidate();
                    // 重新选择物品后清理之前的 Hover 缓存
                    ItemTooltipProxy.clearTooltipTask(mCachedStack);
                }
            }));
        });
    }
}