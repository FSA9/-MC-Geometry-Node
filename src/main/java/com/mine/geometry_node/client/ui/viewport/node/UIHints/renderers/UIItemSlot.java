package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.utils.ItemTooltipProxy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ItemStackTooltipOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.utils.ItemCodecUtils;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.mc.MinecraftSurfaceView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import javax.annotation.Nonnull;

public class UIItemSlot extends FrameLayout {
    private static final float ITEM_SIZE_GUI = 16f;
    private static final float ITEM_PADDING_GUI = 4f;
    private static final float ITEM_MAX_SCALE = 1.25f;

    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();

    private volatile ItemStack mCachedStack = ItemStack.EMPTY;
    private String mLastJson = null;
    private long mLastClickTime = 0;
    private MinecraftSurfaceView mSurfaceView;
    private volatile float mViewportScale = 1.0f;
    private int mLastSurfaceWidth = -1;
    private int mLastSurfaceHeight = -1;

    public UIItemSlot(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        super(context);
        this.mNodeData = nodeData;
        this.mPortId = portId;
        this.mEditorContext = editorContext;

        setWillNotDraw(false);
        setClipChildren(false);
        setOnHoverListener((v, event) -> {
            handleHover(event);
            return true;
        });
        updateCache();

        mSurfaceView = new MinecraftSurfaceView(context);
        mSurfaceView.setEnabled(false);
        mSurfaceView.setClickable(false);
        mSurfaceView.setFocusable(false);
        mSurfaceView.setRenderer(new MinecraftSurfaceView.Renderer() {
            @Override
            public void onSurfaceChanged(int width, int height) {}

            @Override
            public void onDraw(@Nonnull GuiGraphics gr, int mouseX, int mouseY, float deltaTick, double guiScale, float alpha) {
                if (!mCachedStack.isEmpty()) {
                    gr.pose().pushPose();

                    float safeGuiScale = guiScale > 0.0 ? (float) guiScale : 1.0f;
                    float viewportScale = mViewportScale;
                    float slotGuiW = UIItemSlot.this.getWidth() * viewportScale / safeGuiScale;
                    float slotGuiH = UIItemSlot.this.getHeight() * viewportScale / safeGuiScale;
                    float padding = ITEM_PADDING_GUI * viewportScale;
                    float contentSize = Math.max(1.0f, Math.min(slotGuiW, slotGuiH) - padding * 2.0f);
                    float itemScale = Math.min(ITEM_MAX_SCALE * viewportScale, contentSize / ITEM_SIZE_GUI);
                    float drawX = (slotGuiW - ITEM_SIZE_GUI * itemScale) / 2.0f;
                    float drawY = (slotGuiH - ITEM_SIZE_GUI * itemScale) / 2.0f;

                    gr.pose().translate(drawX, drawY, 0.0f);
                    gr.pose().scale(itemScale, itemScale, 1.0f);
                    gr.renderItem(mCachedStack, 0, 0);
                    gr.renderItemDecorations(Minecraft.getInstance().font, mCachedStack, 0, 0);

                    gr.pose().popPose();
                }
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mSurfaceView, lp);
    }

    public void setViewportScale(float scale) {
        float safeScale = scale > 0.0f ? scale : 1.0f;
        boolean scaleChanged = Math.abs(mViewportScale - safeScale) > 0.001f;
        if (!scaleChanged && mLastSurfaceWidth >= 0 && mLastSurfaceHeight >= 0) return;

        mViewportScale = safeScale;
        updateSurfaceBounds();
        if (scaleChanged && mSurfaceView != null) {
            mSurfaceView.invalidate();
        }
    }

    private void updateSurfaceBounds() {
        if (mSurfaceView == null || getWidth() <= 0 || getHeight() <= 0) return;

        float viewportScale = Math.max(1.0f, mViewportScale);
        int surfaceWidth = Math.max(1, Math.round(getWidth() * viewportScale));
        int surfaceHeight = Math.max(1, Math.round(getHeight() * viewportScale));
        if (surfaceWidth == mLastSurfaceWidth && surfaceHeight == mLastSurfaceHeight) return;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mSurfaceView.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(surfaceWidth, surfaceHeight);
        } else {
            lp.width = surfaceWidth;
            lp.height = surfaceHeight;
        }
        lp.leftMargin = 0;
        lp.topMargin = 0;
        mSurfaceView.setLayoutParams(lp);
        mSurfaceView.invalidate();

        mLastSurfaceWidth = surfaceWidth;
        mLastSurfaceHeight = surfaceHeight;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateSurfaceBounds();
    }

    private void updateCache() {
        Object rawVal = mNodeData.inputs.get(mPortId);
        String json = rawVal instanceof String ? (String) rawVal : "";
        if (!json.equals(mLastJson)) {
            ItemStack previousStack = mCachedStack;
            mLastJson = json;
            if (Minecraft.getInstance().level != null) {
                mCachedStack = ItemCodecUtils.fromJson(json, Minecraft.getInstance().level.registryAccess());
            } else {
                mCachedStack = ItemStack.EMPTY;
            }
            ItemTooltipProxy.clearTooltipTask(previousStack);
            if (mSurfaceView != null) {
                mSurfaceView.invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateCache();

        float w = getWidth();
        float h = getHeight();
        float radius = UIUtils.dp2px(3.0f);
        float stroke = UIUtils.dp2px(1.0f);
        float inset = UIUtils.dp2px(3.0f);
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFF171A1F);
        mTempRect.set(0, 0, w, h);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(0xFF4D535C);
        mTempRect.set(stroke / 2.0f, stroke / 2.0f, w - stroke / 2.0f, h - stroke / 2.0f);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFF252A31);
        mTempRect.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(mTempRect, radius * 0.6f, radius * 0.6f, radius * 0.6f, radius * 0.6f, mPaint);

        mPaint.setColor(0x44343A43);
        canvas.drawRect(inset, inset, w - inset, inset + stroke, mPaint);
        canvas.drawRect(inset, inset, inset + stroke, h - inset, mPaint);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        handleHover(event);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        super.dispatchGenericMotionEvent(event);
        handleHover(event);
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        updateCache();
        if (mCachedStack.isEmpty()) {
            ItemStackTooltipOverlay.hide();
            return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
        }
        ItemStackTooltipOverlay.showForEvent(this, mCachedStack, event);
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    private void handleHover(MotionEvent event) {
        updateCache();
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            if (mCachedStack.isEmpty()) {
                ItemStackTooltipOverlay.hide();
                return;
            }

            ItemStackTooltipOverlay.showForEvent(this, mCachedStack, event);

        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            ItemTooltipProxy.clearTooltipTask(mCachedStack);
            ItemStackTooltipOverlay.hide();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ItemTooltipProxy.clearTooltipTask(mCachedStack);
        ItemStackTooltipOverlay.hide();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
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

        InventoryItemPickerOverlay.showFor(this, pickedStack -> {
            if (mEditorContext == null || mc.level == null) {
                return;
            }
            String newJson = ItemCodecUtils.toJson(pickedStack, mc.level.registryAccess());
            UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, newJson);
            updateCache();
            invalidate();
            ItemTooltipProxy.clearTooltipTask();
            ItemStackTooltipOverlay.hide();
        }, () -> {
            requestFocus();
        });
    }
}
