package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
import com.mine.geometry_node.client.entitypreview.EntityPreviewClient;
import com.mine.geometry_node.client.ui.components.integration.nativepreview.ViewportNativePreviewView;
import com.mine.geometry_node.client.ui.components.integration.nativepreview.DeferredEntityPreviewGuard;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.ViewConfiguration;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Reusable entity-template preview without graph-editor state or commands. */
public class EntityTemplatePreviewView extends ViewportNativePreviewView {
    private static final float PADDING_DP = 5.0f;
    private static final float ROTATION_SENSITIVITY = 0.65f;
    private static final float DEFAULT_YAW = 25.0f;
    private static final float DEFAULT_PITCH = -10.0f;

    private static EntityTemplateValue sClipboardTemplate;

    private final ValuePreviewStyle mPreviewStyle;
    private final Paint mPaint = new Paint();
    private final RectF mTempRect = new RectF();
    private final PreviewZoomController mZoom = new PreviewZoomController(() -> {
        requestNativePreviewRender();
        invalidate();
    });
    private final int mTouchSlop;

    public enum Status { EMPTY, LOADING, READY, UNAVAILABLE, NO_APPEARANCE, ERROR }
    private record Reference(UUID id, String dimension) {}
    private volatile Reference mReference;
    private volatile Status mStatus = Status.EMPTY;
    private volatile Consumer<Status> mStatusListener;
    private volatile long mSourceRevision;
    private Level mTemplateLevel;
    private boolean mTemplateAttempted;
    private boolean mPreviewAttached;
    private WeakReference<Entity> mFailedRenderEntity = new WeakReference<>(null);
    private EntityTemplateValue mReferenceTemplate = EntityTemplateValue.EMPTY;
    private Object mReferenceConnection;
    private Status mReferenceStatus = Status.UNAVAILABLE;
    private final Runnable mNotifyStatus = () -> {
        if (mStatusListener != null) mStatusListener.accept(mStatus);
    };

    private volatile Entity mPreviewEntity;
    private volatile EntityTemplateValue mDisplayTemplate = EntityTemplateValue.EMPTY;
    private Object mLastRawValue;
    private Runnable mDisplayClickAction;
    private Consumer<EntityTemplateValue> mDisplayPasteAction;
    private boolean mEditable;
    private volatile RotationMode mRotationMode;
    private volatile float mYaw = DEFAULT_YAW;
    private volatile float mPitch = DEFAULT_PITCH;
    private float mDownX;
    private float mDownY;
    private float mLastX;
    private float mLastY;
    private boolean mPressed;
    private boolean mDragging;

    public EntityTemplatePreviewView(Context context) {
        this(context, RotationMode.HORIZONTAL);
    }

    public EntityTemplatePreviewView(Context context, RotationMode rotationMode) {
        this(context, rotationMode, ValuePreviewStyle.DEFAULT);
    }

    public EntityTemplatePreviewView(Context context, RotationMode rotationMode, ValuePreviewStyle style) {
        super(context);
        mPreviewStyle = style != null ? style : ValuePreviewStyle.DEFAULT;
        mRotationMode = rotationMode != null ? rotationMode : RotationMode.HORIZONTAL;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false);
        setClipChildren(false);
        updateFocusableState();
        setOnFocusChangeListener((view, focused) -> invalidate());
    }

    public final ValuePreviewStyle getPreviewStyle() {
        return mPreviewStyle;
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
        refreshDisplayValue();
        long revision = mSourceRevision;
        Entity entity = null;
        try {
            entity = resolveRenderEntity(revision);
            if (revision != mSourceRevision) return;
            if (entity == null) {
                Status status = mReference != null ? mReferenceStatus
                        : mDisplayTemplate.isEmpty() ? Status.EMPTY
                        : mTemplateAttempted ? Status.ERROR : Status.UNAVAILABLE;
                updateStatus(revision, status);
                return;
            }
            if (entity instanceof net.minecraft.world.entity.Interaction) {
                updateStatus(revision, Status.NO_APPEARANCE);
                return;
            }
            synchronized (this) {
                if (mFailedRenderEntity.get() == entity) {
                    updateStatus(revision, Status.ERROR);
                    return;
                }
            }
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
            if (state instanceof ItemEntityRenderState itemState) {
                DroppedItemFrame frame = frameDroppedItem(itemState, rotation);
                width = frame.width();
                height = frame.height();
                translation = frame.translation();
            }
            float size = Math.min(contentWidth / width, contentHeight / height) * 0.82f * mZoom.getFactor();
            if (revision != mSourceRevision) return;
            Entity renderedEntity = entity;
            DeferredEntityPreviewGuard.mark(state, failure -> renderFailed(revision, renderedEntity));
            graphics.entity(state, size, translation, rotation, xRotation, x0, y0, x1, y1);
            updateStatus(revision, Status.READY);
        } catch (RuntimeException failure) {
            if (failure instanceof ReportedException reported && reported.getCause() instanceof Error) throw failure;
            // Some addon entities require world state the preview renderer does not provide.
            renderFailed(revision, entity);
        }
    }

    /** Fits the rendered GROUND model, which can extend well beyond an item entity's collision box. */
    static DroppedItemFrame frameDroppedItem(ItemEntityRenderState state, Quaternionf rotation) {
        // Only the freshly extracted preview state is changed; the live entity keeps its animation.
        state.shouldBob = false;
        state.bobOffset = 0.0f;
        state.ageInTicks = 0.0f;
        state.count = 1;
        state.shouldSpread = false;

        AABB bounds = state.item.getModelBoundingBox();
        // ItemEntityRenderer lifts the model by -minY + 1/16 even when bobbing is disabled.
        Vector3f center = new Vector3f((float) ((bounds.minX + bounds.maxX) / 2.0),
                (float) (bounds.getYsize() / 2.0 + 0.0625),
                (float) ((bounds.minZ + bounds.maxZ) / 2.0));
        Vector3f translation = rotation.transform(center).negate();
        Matrix3f matrix = new Matrix3f().rotation(rotation);
        float xSize = (float) bounds.getXsize();
        float ySize = (float) bounds.getYsize();
        float zSize = (float) bounds.getZsize();
        float width = Math.abs(matrix.m00()) * xSize + Math.abs(matrix.m10()) * ySize + Math.abs(matrix.m20()) * zSize;
        float height = Math.abs(matrix.m01()) * xSize + Math.abs(matrix.m11()) * ySize + Math.abs(matrix.m21()) * zSize;
        // Degenerate/custom models must not produce an infinite or excessive fitting scale.
        return new DroppedItemFrame(Math.max(0.25f, width), Math.max(0.25f, height), translation);
    }

    record DroppedItemFrame(float width, float height, Vector3f translation) {}

    private synchronized void renderFailed(long revision, Entity entity) {
        if (!mPreviewAttached || revision != mSourceRevision) return;
        // Do not queue the same failing renderer every frame or retain a live world entity.
        mFailedRenderEntity = new WeakReference<>(entity);
        updateStatus(Status.ERROR);
    }

    /** Content zoom is opt-in and independent of the surrounding viewport's scale. */
    public final PreviewZoomController zoom() { return mZoom; }

    public final void setViewportScale(float scale) {
        updateNativePreviewScale(scale);
    }

    public final void setViewportTransform(float scale, float windowLeftPx, float windowTopPx) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, getNativePreviewOrder());
    }

    public final void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        updateNativePreviewTransform(scale, windowLeftPx, windowTopPx, previewOrder);
    }

    public synchronized void setDisplayTemplate(EntityTemplateValue template) {
        boolean wasReference = mReference != null;
        mReference = null;
        if (wasReference) {
            clearReferencePreview();
            mFailedRenderEntity.clear();
        }
        applyTemplate(template != null ? template : EntityTemplateValue.EMPTY);
        if (wasReference) {
            mSourceRevision++;
            updateStatus(mDisplayTemplate.isEmpty() ? Status.EMPTY : Status.UNAVAILABLE);
            requestNativePreviewRender();
        }
    }

    /** Non-owning reference: resolving or detaching this preview never removes a world entity. */
    public synchronized void setDisplayReference(UUID entityId, String dimension) {
        Reference next = entityId == null ? null : new Reference(entityId, dimension == null ? "" : dimension);
        if (Objects.equals(mReference, next) && mDisplayTemplate.isEmpty()) return;
        mFailedRenderEntity.clear();
        applyTemplate(EntityTemplateValue.EMPTY);
        clearReferencePreview();
        mReference = next;
        mTemplateLevel = null;
        mTemplateAttempted = false;
        mSourceRevision++;
        updateStatus(next == null ? Status.EMPTY : Status.UNAVAILABLE);
        requestNativePreviewRender();
    }

    public final Status getPreviewStatus() { return mStatus; }

    /** Listener runs on the UI thread; references are resolved only during native rendering. */
    public final void setOnPreviewStatusChanged(Consumer<Status> listener) {
        mStatusListener = listener;
        if (listener != null) listener.accept(mStatus);
    }

    private synchronized void updateStatus(long revision, Status status) {
        if (revision == mSourceRevision) updateStatus(status);
    }

    private void updateStatus(Status status) {
        if (mStatus == status) return;
        mStatus = status;
        if (mStatusListener != null && isAttachedToWindow()) {
            removeCallbacks(mNotifyStatus);
            post(mNotifyStatus);
        }
    }

    private synchronized Entity resolveRenderEntity(long expectedRevision) {
        // A callback may already be running when the UI detaches or rebinds this cell.
        // Fence requests and construction under the same lock used by detach/source changes.
        if (!mPreviewAttached || expectedRevision != mSourceRevision) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            replacePreviewEntity(null);
            mTemplateLevel = null;
            mTemplateAttempted = false;
            clearReferencePreview();
            return null;
        }
        Reference reference = mReference;
        if (reference != null) {
            // Render live client entities when available; never retain or dispose them as owned previews.
            boolean sameDimension = reference.dimension().isEmpty()
                    || minecraft.level.dimension().identifier().toString().equals(reference.dimension());
            Entity live = sameDimension ? minecraft.level.getEntity(reference.id()) : null;
            if (live != null && !live.isRemoved()) {
                clearReferencePreview();
                return live;
            }
            if (mTemplateLevel != minecraft.level || mReferenceConnection != minecraft.getConnection()) {
                clearReferencePreview();
                mTemplateLevel = minecraft.level;
                mReferenceConnection = minecraft.getConnection();
            }
            // Only visible native surfaces reach this method. Resolving queues bounded network work,
            // never loads a chunk or creates an entity in a world entity manager.
            EntityPreviewClient.Result result = EntityPreviewClient.INSTANCE.resolve(reference.id(), reference.dimension());
            if (result.state() == EntityPreviewClient.State.READY) {
                EntityTemplateValue template = result.template();
                if (!template.equals(mReferenceTemplate)) {
                    replacePreviewEntity(null);
                    mReferenceTemplate = template;
                    mTemplateAttempted = false;
                }
                if (!mTemplateAttempted && !template.isEmpty()) {
                    mTemplateAttempted = true;
                    replacePreviewEntity(createReferenceEntity(template, minecraft.level));
                }
                mReferenceStatus = mPreviewEntity == null ? Status.ERROR : Status.READY;
            } else if (result.state() == EntityPreviewClient.State.LOADING) {
                // Keep the previous detached appearance while refreshing so expiry causes no blink.
                mReferenceStatus = Status.LOADING;
            } else {
                clearReferencePreview();
                mReferenceStatus = result.state() == EntityPreviewClient.State.ERROR ? Status.ERROR : Status.UNAVAILABLE;
            }
            return mPreviewEntity;
        }
        if (mTemplateLevel != minecraft.level) {
            replacePreviewEntity(null);
            mTemplateLevel = minecraft.level;
            mTemplateAttempted = false;
        }
        if (!mTemplateAttempted && !mDisplayTemplate.isEmpty()) {
            mTemplateAttempted = true;
            replacePreviewEntity(mDisplayTemplate.create(minecraft.level, Vec3.ZERO));
        }
        return mPreviewEntity;
    }

    private static Entity createReferenceEntity(EntityTemplateValue template, Level level) {
        var data = template.data();
        boolean tamed = data.getBooleanOr("Tame", false);
        Entity entity = new EntityTemplateValue(template.entityTypeId(), data).create(level, Vec3.ZERO);
        try {
            // Collars require the tame flag, but a preview has no owner or world relationships.
            if (entity instanceof TamableAnimal animal) animal.setTame(tamed, false);
            return entity;
        } catch (RuntimeException failure) {
            if (entity != null) entity.discard();
            throw failure;
        }
    }

    public void setDisplayClickAction(Runnable action) {
        mDisplayClickAction = action;
        if (action != null) setEditable(true);
    }

    public void setDisplayPasteAction(Consumer<EntityTemplateValue> action) {
        mDisplayPasteAction = action;
        if (action != null) setEditable(true);
    }

    public void openTemplateEditor() {
        if (!isEnabled()) return;
        if (mDisplayClickAction != null) {
            mDisplayClickAction.run();
        } else {
            onOpenEditorRequested();
        }
    }

    protected final void setEditable(boolean editable) {
        mEditable = editable;
        updateFocusableState();
    }

    protected final boolean hasDisplayClickAction() {
        return mDisplayClickAction != null;
    }

    protected final boolean dispatchDisplayPaste(EntityTemplateValue template) {
        if (mDisplayPasteAction == null) return false;
        mDisplayPasteAction.accept(template);
        setDisplayTemplate(template);
        return true;
    }

    protected void refreshDisplayValue() {
    }

    protected void onOpenEditorRequested() {
    }

    protected final synchronized void applyTemplate(Object raw) {
        if (Objects.equals(raw, mLastRawValue)) return;
        mLastRawValue = raw;
        EntityTemplateValue template = EntityTemplateValue.from(raw);
        if (template.equals(mDisplayTemplate)) return;
        replacePreviewEntity(null);
        mDisplayTemplate = template;
        mTemplateLevel = null;
        mTemplateAttempted = false;
        mSourceRevision++;
        // Entity construction is deferred to the native render thread, where a client level is available.
        updateStatus(template.isEmpty() ? Status.EMPTY : Status.UNAVAILABLE);
        requestNativePreviewRender();
        invalidate();
    }

    protected final void invalidateDisplayValue() {
        mLastRawValue = null;
    }

    private void updateFocusableState() {
        setFocusable(mEditable);
        setFocusableInTouchMode(mEditable);
        setClickable(mEditable);
    }

    public RotationMode getRotationMode() {
        return mRotationMode;
    }

    public void setRotationMode(RotationMode mode) {
        RotationMode resolved = mode != null ? mode : RotationMode.HORIZONTAL;
        if (resolved == mRotationMode) return;
        mRotationMode = resolved;
        if (resolved == RotationMode.FIXED) {
            mYaw = DEFAULT_YAW;
            mPitch = DEFAULT_PITCH;
        } else if (resolved == RotationMode.HORIZONTAL) {
            mPitch = DEFAULT_PITCH;
        }
        requestNativePreviewRender();
        invalidate();
    }

    private synchronized void replacePreviewEntity(Entity entity) {
        Entity previous = mPreviewEntity;
        mPreviewEntity = entity;
        mFailedRenderEntity.clear();
        if (previous != null && previous != entity) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && !minecraft.isSameThread()) minecraft.execute(previous::discard);
            else previous.discard();
        }
    }

    private void clearReferencePreview() {
        if (!mReferenceTemplate.isEmpty()) replacePreviewEntity(null);
        mReferenceTemplate = EntityTemplateValue.EMPTY;
        mReferenceConnection = null;
        mReferenceStatus = Status.UNAVAILABLE;
        mTemplateAttempted = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        refreshDisplayValue();

        float width = getWidth();
        float height = getHeight();
        float radius = UIUtils.dp2px(mPreviewStyle.cornerRadiusDp());
        float stroke = UIUtils.dp2px(mPreviewStyle.strokeWidthDp());
        mPaint.setAntiAlias(true);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(mPreviewStyle.backgroundColor());
        mTempRect.set(0, 0, width, height);
        canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);

        if (stroke > 0) {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(stroke);
            mPaint.setColor(isFocused()
                    ? mPreviewStyle.focusBorderColor()
                    : mPreviewStyle.borderColor());
            mTempRect.set(stroke / 2.0f, stroke / 2.0f, width - stroke / 2.0f, height - stroke / 2.0f);
            canvas.drawRoundRect(mTempRect, radius, radius, radius, radius, mPaint);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mEditable) return false;
        return onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mEditable) return false;
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
            if (!mDragging && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) mDragging = true;
            if (mDragging) {
                boolean rotationChanged = false;
                if (mRotationMode.allowsYaw()) {
                    mYaw += (x - mLastX) * ROTATION_SENSITIVITY;
                    rotationChanged = true;
                }
                if (mRotationMode.allowsPitch()) {
                    mPitch = Math.max(-75.0f, Math.min(75.0f, mPitch + (y - mLastY) * ROTATION_SENSITIVITY));
                    rotationChanged = true;
                }
                if (rotationChanged) requestNativePreviewRender();
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
                boolean openEditor = isFocused() && hasDisplayClickAction();
                requestFocus();
                invalidate();
                if (openEditor) openTemplateEditor();
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
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isEnabled() && mZoom.handleScroll(event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event) {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isEnabled() && mEditable && isFocused()) {
            KeyBinding copy = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_COPY));
            if (copy != null && UiInputBindings.matches(copy, event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) sClipboardTemplate = mDisplayTemplate;
                return true;
            }
            KeyBinding paste = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_PASTE));
            if (paste != null && UiInputBindings.matches(paste, event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && sClipboardTemplate != null) {
                    commitTemplate(sClipboardTemplate);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (!isEnabled()) {
            mPressed = false;
            mDragging = false;
            if (isPressed()) setPressed(false);
        }
        invalidate();
    }

    protected void commitTemplate(EntityTemplateValue template) {
        if (template == null || template.isEmpty()) return;
        dispatchDisplayPaste(template);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        if (!focused) {
            mPressed = false;
            mDragging = false;
            setPressed(false);
        }
        super.onWindowFocusChanged(focused);
    }

    @Override
    protected void onAttachedToWindow() {
        synchronized (this) { mPreviewAttached = true; }
        super.onAttachedToWindow();
        if (mStatusListener != null) post(mNotifyStatus);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mNotifyStatus);
        mPressed = false;
        mDragging = false;
        setPressed(false);
        synchronized (this) {
            mPreviewAttached = false;
            replacePreviewEntity(null);
            clearReferencePreview();
            mTemplateLevel = null;
            mTemplateAttempted = false;
            mSourceRevision++;
            mStatus = mReference == null && mDisplayTemplate.isEmpty() ? Status.EMPTY : Status.UNAVAILABLE;
        }
        super.onDetachedFromWindow();
    }

    public enum RotationMode {
        FIXED(false, false),
        HORIZONTAL(true, false),
        FREE(true, true);

        private final boolean allowsYaw;
        private final boolean allowsPitch;

        RotationMode(boolean allowsYaw, boolean allowsPitch) {
            this.allowsYaw = allowsYaw;
            this.allowsPitch = allowsPitch;
        }

        public boolean allowsYaw() {
            return allowsYaw;
        }

        public boolean allowsPitch() {
            return allowsPitch;
        }
    }
}
