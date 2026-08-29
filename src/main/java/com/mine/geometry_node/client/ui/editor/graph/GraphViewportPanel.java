package com.mine.geometry_node.client.ui.editor.graph;

import com.mine.geometry_node.client.ui.editor.asset.drag.AssetDragDropRegistry;
import com.mine.geometry_node.client.ui.editor.asset.drag.AssetDragState;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionId;
import com.mine.geometry_node.client.ui.editor.graph.action.ViewportActionRequest;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.HorizontalScrollView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

/**
 * 视口面板容器 (包含顶部的 Tab 栏 + 下方的 viewport)
 */
public class GraphViewportPanel extends LinearLayout {

    // ==========================================
    // 局部 UI 尺寸常量 (单位: DP)
    // ==========================================
    private static final float TAB_BAR_HEIGHT = 30.0f;
    private static final float TEXT_SIZE_TAB = 14.0f;
    private static final float SCROLL_MULTIPLIER = 80.0f; // 滚轮速度乘数，不用转DP

    // Padding 常量
    private static final float PADDING_HANDLE_L = 8.0f;
    private static final float PADDING_HANDLE_R = 4.0f;
    private static final float PADDING_TITLE_L = 4.0f;
    private static final float PADDING_TITLE_R = 8.0f;
    private static final float PADDING_CLOSE = 8.0f;

    private static final float INDICATOR_WIDTH = 2.0f;

    private static GraphViewportPanel sFocusedPanel;

    private final HorizontalScrollView mTabScrollView;
    private final TabBarLayout mTabBar;
    private final Viewport mViewport;
    private final float mTouchSlop;
    private final Runnable mTabChangedListener = () -> post(this::refreshTabs);
    private final AssetDragDropRegistry.DropTarget mDropTarget = this::acceptAssetDrop;
    private GraphSession mSelectedSession;
    private long mObservedOpenSessionSerial = -1L;
    private boolean mManualSessionSelection;
    private boolean mCallbacksRegistered;
    private Consumer<GraphSession> mSessionChangedListener;
    private Consumer<GraphSession> mBeforeSessionSaveListener;
    private GraphSession mLastNotifiedSession;
    private Runnable mInteractionListener;

    public GraphViewportPanel(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mTabScrollView = new HorizontalScrollView(context);
        mTabScrollView.setHorizontalScrollBarEnabled(false);
        mTabScrollView.setBackground(createColorDrawable(0xFF1E1E1E));

        mTabScrollView.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                float vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (vScroll != 0) {
                    mTabScrollView.scrollBy((int) (-vScroll * SCROLL_MULTIPLIER), 0);
                    return true;
                }
            }
            return false;
        });

        mTabBar = new TabBarLayout(context);
        mTabScrollView.addView(mTabBar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(mTabScrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp2pxInt(TAB_BAR_HEIGHT)));

        mViewport = new Viewport(context);
        addView(mViewport, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        refreshTabs();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        activatePanel();
    }

    @Override
    protected void onDetachedFromWindow() {
        deactivatePanel();
        super.onDetachedFromWindow();
    }

    public void activatePanel() {
        if (sFocusedPanel == null) {
            sFocusedPanel = this;
        }
        registerCallbacks();
        mViewport.activateLifecycle();
        refreshTabs();
    }

    public void deactivatePanel() {
        if (sFocusedPanel == this) {
            sFocusedPanel = null;
        }
        unregisterCallbacks();
        mViewport.deactivateLifecycle();
    }

    public void setSessionChangedListener(Consumer<GraphSession> listener) {
        mSessionChangedListener = listener;
        if (mSessionChangedListener != null) {
            mLastNotifiedSession = mViewport.getController().getCurrentSession();
            mSessionChangedListener.accept(mLastNotifiedSession);
        } else {
            mLastNotifiedSession = null;
        }
    }

    public void setBeforeSessionSaveListener(Consumer<GraphSession> listener) {
        mBeforeSessionSaveListener = listener;
    }

    public void setInteractionListener(Runnable listener) {
        mInteractionListener = listener;
    }

    public GraphSession currentSession() {
        return mViewport.getController().getCurrentSession();
    }

    public void saveCurrentSession() {
        mViewport.getController().performAction(ViewportActionId.SAVE, ViewportActionRequest.EMPTY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            markFocused();
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean acceptAssetDrop(AssetDragState.Payload payload, float rawX, float rawY) {
        if (payload == null || !payload.isSingleJsonGraph()) return false;
        GraphSession targetSession = mViewport.getController().getCurrentSession();
        if (!isReadyForImport(targetSession)) return false;
        if (!isRawPointInside(mViewport, rawX, rawY)) return false;

        int[] viewportLoc = new int[2];
        mViewport.getLocationOnScreen(viewportLoc);
        float viewportX = rawX - viewportLoc[0];
        float viewportY = rawY - viewportLoc[1];
        importDraggedGraph(payload.entry(), viewportX, viewportY, targetSession);
        return true;
    }

    private void importDraggedGraph(AssetEntry entry, float viewportX, float viewportY, GraphSession targetSession) {
        if (!isReadyForImport(targetSession)) return;
        if (entry.sourceKind() == AssetSourceKind.LOCAL) {
            if (entry.localFile() == null || entry.localFile().isDirectory()) return;
            try {
                mViewport.getController().executeImportGraphJson(Files.readString(entry.localFile().toPath()), viewportX, viewportY);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (entry.sourceKind() == AssetSourceKind.REMOTE && RemoteGraphClientState.canDownload()) {
            Path temporary = Path.of(System.getProperty("java.io.tmpdir"), "geometrynode-graph-import",
                    UUID.randomUUID() + ".json");
            UUID jobId = ClientAssetTransferService.INSTANCE.submit(java.util.List.of(
                    ClientAssetTransferRequest.download(entry.path(), temporary, AssetTransferConflictPolicy.OVERWRITE)));
            ClientAssetTransferService.INSTANCE.completion(jobId).thenCompose(job -> {
                if (job.completedFileCount() != job.files().size()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("remote graph download failed"));
                }
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return Files.readString(temporary);
                    } catch (Exception exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    } finally {
                        try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
                    }
                });
            }).whenComplete((json, throwable) -> post(() -> {
                if (throwable != null) {
                    System.err.println("[GraphViewportPanel] Remote graph import failed: " + throwable.getMessage());
                    return;
                }
                if (isReadyForImport(targetSession)) {
                    mViewport.getController().executeImportGraphJson(json, viewportX, viewportY);
                }
            }));
        }
    }

    private boolean isReadyForImport(GraphSession targetSession) {
        return mCallbacksRegistered
                && targetSession != null
                && mViewport.getController().getCurrentSession() == targetSession
                && mViewport.getController().hasActiveSession();
    }

    private boolean isRawPointInside(View view, float rawX, float rawY) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + view.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + view.getHeight();
    }

    private void refreshTabs() {
        mTabBar.removeAllViews();
        DocumentManager docMgr = DocumentManager.INSTANCE;
        applyOpenedSession(docMgr);
        GraphSession selectedSession = resolveSelectedSession(docMgr);
        mViewport.getController().bindSession(selectedSession);
        if (mSessionChangedListener != null && selectedSession != mLastNotifiedSession) {
            mLastNotifiedSession = selectedSession;
            mSessionChangedListener.accept(selectedSession);
        }

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        for (int i = 0; i < docMgr.getSessions().size(); i++) {
            GraphSession session = docMgr.getSessions().get(i);
            mTabBar.addView(createTabView(session, i), tabParams);
        }
    }

    private View createTabView(GraphSession session, final int index) {
        DocumentManager docMgr = DocumentManager.INSTANCE;
        LinearLayout tabLayout = new LinearLayout(getContext());
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setGravity(Gravity.CENTER_VERTICAL);

        boolean isActive = (session == mSelectedSession);
        tabLayout.setBackground(createColorDrawable(isActive ? 0xFF3C3C3C : 0xFF2A2A2A));

        TextView dragHandle = UIUtils.createLockedTextView(getContext(), " ⋮⋮ ", TEXT_SIZE_TAB, 0xFF666666);
        dragHandle.setPadding(dp2pxInt(PADDING_HANDLE_L), 0, dp2pxInt(PADDING_HANDLE_R), 0);
        tabLayout.addView(dragHandle);

        LinearLayout contentArea = new LinearLayout(getContext());
        contentArea.setOrientation(LinearLayout.HORIZONTAL);
        contentArea.setGravity(Gravity.CENTER_VERTICAL);
        contentArea.setPadding(dp2pxInt(PADDING_TITLE_L), 0, dp2pxInt(PADDING_TITLE_R), 0);

        String titleText = (session.isDirty ? "* " : "") + session.tabName;
        TextView titleView = UIUtils.createLockedTextView(getContext(), titleText, TEXT_SIZE_TAB, isActive ? 0xFFFFFFFF : 0xFFAAAAAA);
        contentArea.addView(titleView);

        TextView closeBtn = UIUtils.createLockedTextView(getContext(), " × ", TEXT_SIZE_TAB, isActive ? 0xFFCCCCCC : 0xFF888888);
        int padClose = dp2pxInt(PADDING_CLOSE);
        closeBtn.setPadding(padClose, 0, padClose, 0);
        closeBtn.setOnClickListener(v -> {
            if (mBeforeSessionSaveListener != null) {
                mBeforeSessionSaveListener.accept(session);
            }
            if (DocumentManager.INSTANCE.saveSession(session)) {
                v.post(() -> docMgr.closeSession(session));
            }
        });
        contentArea.addView(closeBtn);

        tabLayout.addView(contentArea);

        contentArea.setOnClickListener(v -> selectSession(session));

        final float[] startX = {0};
        final boolean[] isDragging = {false};

        dragHandle.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    startX[0] = event.getRawX();
                    isDragging[0] = false;
                    mTabScrollView.requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX[0];
                    if (!isDragging[0] && Math.abs(dx) > mTouchSlop) {
                        isDragging[0] = true;
                        tabLayout.setAlpha(0.4f);
                    }
                    if (isDragging[0]) {
                        int[] tabBarLoc = new int[2];
                        mTabBar.getLocationOnScreen(tabBarLoc);
                        float dropX = event.getRawX() - tabBarLoc[0];

                        int indicatorX = mTabBar.getWidth();
                        for (int i = 0; i < mTabBar.getChildCount(); i++) {
                            View child = mTabBar.getChildAt(i);
                            float centerX = child.getLeft() + child.getWidth() / 2f;
                            if (dropX < centerX) {
                                indicatorX = child.getLeft();
                                break;
                            }
                        }
                        mTabBar.updateIndicator(indicatorX);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging[0]) {
                        calculateAndMoveTab(event.getRawX(), index);
                    }
                    tabLayout.setAlpha(1.0f);
                    mTabBar.hideIndicator();
                    mTabScrollView.requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        });

        return tabLayout;
    }

    private void selectSession(GraphSession session) {
        if (session == null) return;
        markFocused();
        mManualSessionSelection = true;
        mSelectedSession = session;
        DocumentManager.INSTANCE.switchSession(session);
        refreshTabs();
    }

    private void markFocused() {
        sFocusedPanel = this;
        if (mInteractionListener != null) mInteractionListener.run();
    }

    private void applyOpenedSession(DocumentManager docMgr) {
        long openSerial = docMgr.getOpenSessionSerial();
        if (mObservedOpenSessionSerial == openSerial) {
            return;
        }
        mObservedOpenSessionSerial = openSerial;

        GraphSession openedSession = docMgr.getLastOpenedSession();
        if (openedSession == null || !docMgr.getSessions().contains(openedSession)) {
            return;
        }
        if (sFocusedPanel == this || (sFocusedPanel == null && !mManualSessionSelection)) {
            mSelectedSession = openedSession;
        }
    }

    private GraphSession resolveSelectedSession(DocumentManager docMgr) {
        if (mSelectedSession != null && docMgr.getSessions().contains(mSelectedSession)) {
            return mSelectedSession;
        }
        GraphSession activeSession = docMgr.getActiveSession();
        if (activeSession != null && docMgr.getSessions().contains(activeSession)) {
            mSelectedSession = activeSession;
            return mSelectedSession;
        }
        mSelectedSession = docMgr.getSessions().isEmpty() ? null : docMgr.getSessions().get(docMgr.getSessions().size() - 1);
        return mSelectedSession;
    }

    private void registerCallbacks() {
        if (mCallbacksRegistered) {
            return;
        }
        DocumentManager.INSTANCE.addOnTabChangedListener(mTabChangedListener);
        AssetDragDropRegistry.registerDropTarget(mDropTarget);
        mCallbacksRegistered = true;
    }

    private void unregisterCallbacks() {
        if (!mCallbacksRegistered) {
            return;
        }
        DocumentManager.INSTANCE.removeOnTabChangedListener(mTabChangedListener);
        AssetDragDropRegistry.unregisterDropTarget(mDropTarget);
        mCallbacksRegistered = false;
    }

    private void calculateAndMoveTab(float rawX, int currentIndex) {
        DocumentManager docMgr = DocumentManager.INSTANCE;
        int[] tabBarLoc = new int[2];
        mTabBar.getLocationOnScreen(tabBarLoc);

        float dropX = rawX - tabBarLoc[0];
        int targetIdx = mTabBar.getChildCount();

        for (int i = 0; i < mTabBar.getChildCount(); i++) {
            View child = mTabBar.getChildAt(i);
            float centerX = child.getLeft() + child.getWidth() / 2f;
            if (dropX < centerX) {
                targetIdx = i;
                break;
            }
        }

        if (targetIdx > currentIndex) {
            targetIdx--;
        }

        if (targetIdx != currentIndex) {
            final int finalTarget = targetIdx;
            mTabBar.post(() -> docMgr.moveSession(currentIndex, finalTarget));
        }
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static class TabBarLayout extends LinearLayout {
        private int mIndicatorX = -1;
        private final Paint mIndicatorPaint;

        public TabBarLayout(Context context) {
            super(context);
            setOrientation(LinearLayout.HORIZONTAL);
            setWillNotDraw(false);

            mIndicatorPaint = new Paint();
            mIndicatorPaint.setColor(0xFF00AAFF);
        }

        public void updateIndicator(int x) {
            if (mIndicatorX != x) {
                mIndicatorX = x;
                invalidate();
            }
        }

        public void hideIndicator() {
            if (mIndicatorX != -1) {
                mIndicatorX = -1;
                invalidate();
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            if (mIndicatorX >= 0) {
                int indicatorHalfWidth = dp2pxInt(INDICATOR_WIDTH);
                int drawX = Math.max(indicatorHalfWidth, Math.min(getWidth() - indicatorHalfWidth, mIndicatorX));
                canvas.drawRect(drawX - indicatorHalfWidth, 0, drawX + indicatorHalfWidth, getHeight(), mIndicatorPaint);
            }
        }
    }
}
