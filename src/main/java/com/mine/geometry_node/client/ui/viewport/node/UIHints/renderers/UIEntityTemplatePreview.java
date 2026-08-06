package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.UIHintValueBinder;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.EntityTemplatePickerController;
import com.mine.geometry_node.client.ui.viewport.preview.ViewportNativePreviewView;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.value.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.ViewConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

public final class UIEntityTemplatePreview extends ViewportNativePreviewView implements ViewportScaledHint, ViewportTransformedHint, InteractiveHintTarget {
    private static final float PADDING_DP = 5.0f;
    private static final float ROTATION_SENSITIVITY = 0.65f;

    private static EntityTemplateValue sClipboardTemplate;

    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final int mTouchSlop;

    private volatile Entity mPreviewEntity;
    private EntityTemplateValue mCachedTemplate = EntityTemplateValue.EMPTY;
    private Object mLastRawValue;
    private volatile float mYaw = 25.0f;
    private volatile float mPitch = -10.0f;
    private float mDownX;
    private float mDownY;
    private float mLastX;
    private float mLastY;
    private boolean mPressed;
    private boolean mDragging;

    public UIEntityTemplatePreview(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        super(context);
        mNodeData = nodeData;
        mPortId = portId;
        mEditorContext = editorContext;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setWillNotDraw(false);
        setClipChildren(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnFocusChangeListener((view, focused) -> invalidate());

        updateCache();
    }

    @Override
    protected void renderNativePreviewContent(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            float guiScale,
            float alpha,
            float windowLeftPx,
            float windowTopPx,
            float surfaceWidthPx,
            float surfaceHeightPx,
            float viewportScale
    ) {
        Entity entity = mPreviewEntity;
        if (entity == null) return;

        try {
            EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, deltaTick);
            state.shadowPieces.clear();
            state.outlineColor = 0;

            float width = Math.max(0.25f, state.boundingBoxWidth);
            float height = Math.max(0.25f, state.boundingBoxHeight);
            int x0 = (int) Math.floor(windowLeftPx / guiScale);
            int y0 = (int) Math.floor(windowTopPx / guiScale);
            int x1 = (int) Math.ceil((windowLeftPx + surfaceWidthPx) / guiScale);
            int y1 = (int) Math.ceil((windowTopPx + surfaceHeightPx) / guiScale);
            float guiWidth = Math.max(1.0f, x1 - x0);
            float guiHeight = Math.max(1.0f, y1 - y0);
            float padding = UIUtils.dp2px(PADDING_DP) * viewportScale / guiScale;
            float contentWidth = Math.max(1.0f, guiWidth - padding * 2.0f);
            float contentHeight = Math.max(1.0f, guiHeight - padding * 2.0f);
            int size = Math.max(1, Math.round(Math.min(contentWidth / width, contentHeight / height) * 0.82f));

            float yaw = mYaw;
            float pitch = mPitch;
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf xRotation = new Quaternionf().rotateX((float) Math.toRadians(pitch));

            if (state instanceof LivingEntityRenderState livingState) {
                livingState.bodyRot = 180.0f + yaw;
                livingState.yRot = 0.0f;
                livingState.xRot = 0.0f;
                livingState.boundingBoxWidth /= Math.max(0.001f, livingState.scale);
                livingState.boundingBoxHeight /= Math.max(0.001f, livingState.scale);
                livingState.scale = 1.0f;
                rotation.mul(xRotation);
            } else {
                rotation.rotateY((float) Math.toRadians(yaw)).mul(xRotation);
            }

            Vector3f translation = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
            graphics.entity(
                    state,
                    size,
                    translation,
                    rotation,
                    xRotation,
                    x0,
                    y0,
                    x1,
                    y1
            );
        } catch (RuntimeException ignored) {
            // Some addon entities require world state their preview renderer does not provide.
        }
    }

    @Override
    public void setViewportScale(float scale) {
        updateNativePreviewScale(scale);
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, getNativePreviewOrder());
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, previewOrder);
    }

    private void updateCache() {
        Object raw = mNodeData != null ? mNodeData.inputs.get(mPortId) : null;
        if (Objects.equals(raw, mLastRawValue)
                && (mCachedTemplate.isEmpty() || mPreviewEntity != null || Minecraft.getInstance().level == null)) {
            return;
        }

        mLastRawValue = raw;
        EntityTemplateValue template = EntityTemplateValue.from(raw);
        if (template.equals(mCachedTemplate) && mPreviewEntity != null) return;

        replacePreviewEntity(null);
        mCachedTemplate = template;
        Minecraft minecraft = Minecraft.getInstance();
        if (!template.isEmpty() && minecraft.level != null) {
            replacePreviewEntity(template.create(minecraft.level, Vec3.ZERO));
        }
        requestNativePreviewRender();
        invalidate();
    }

    private void replacePreviewEntity(Entity entity) {
        Entity previous = mPreviewEntity;
        mPreviewEntity = entity;
        if (previous != null && previous != entity) previous.discard();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateCache();

        float width = getWidth();
        float height = getHeight();
        float radius = UIUtils.dp2px(4.0f);
        float stroke = UIUtils.dp2px(1.0f);
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0xFF171A1F);
        mTempRect.set(0, 0, width, height);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(stroke);
        mPaint.setColor(isFocused() ? 0xFF5F91C7 : 0xFF4D535C);
        mTempRect.set(stroke / 2.0f, stroke / 2.0f, width - stroke / 2.0f, height - stroke / 2.0f);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mPressed = true;
            mDragging = false;
            mDownX = mLastX = event.getX();
            mDownY = mLastY = event.getY();
            setPressed(true);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && mPressed) {
            float x = event.getX();
            float y = event.getY();
            if (!mDragging && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                mDragging = true;
            }
            if (mDragging) {
                mYaw += (x - mLastX) * ROTATION_SENSITIVITY;
                mPitch = Math.max(-75.0f, Math.min(75.0f, mPitch + (y - mLastY) * ROTATION_SENSITIVITY));
                requestNativePreviewRender();
            }
            mLastX = x;
            mLastY = y;
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean clicked = mPressed && !mDragging;
            mPressed = false;
            mDragging = false;
            setPressed(false);
            if (clicked) {
                requestFocus();
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            mPressed = false;
            mDragging = false;
            setPressed(false);
            return true;
        }
        return true;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isFocused()) {
            KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.getConfig().keyBindings.global.copy);
            if (copy != null && copy.matches(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) sClipboardTemplate = mCachedTemplate;
                return true;
            }
            KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.getConfig().keyBindings.global.paste);
            if (paste != null && paste.matches(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && sClipboardTemplate != null) {
                    commitTemplate(sClipboardTemplate);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public void openTemplateEditor() {
        EntityTemplatePickerController.open(this::commitTemplate, this::requestFocus);
    }

    private void commitTemplate(EntityTemplateValue template) {
        if (template == null || template.isEmpty()) return;
        UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, template.toMap());
        mLastRawValue = null;
        updateCache();
    }

    @Override
    protected void onDetachedFromWindow() {
        replacePreviewEntity(null);
        super.onDetachedFromWindow();
    }
}
