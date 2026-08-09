package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.client.ui.editor.asset.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.editor.asset.dialog.OverwriteConfirmDialog;
import com.mine.geometry_node.client.ui.editor.asset.dialog.TransferProgressDialog;
import com.mine.geometry_node.client.ui.editor.asset.dialog.UploadFailureRetryDialog;
import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.navigation.AssetNavigationPanel;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.editor.asset.properties.AssetFilePropertiesTarget;
import com.mine.geometry_node.client.ui.editor.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.editor.asset.service.LocalAssetService;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.common.ResizableDivider;
import com.mine.geometry_node.client.ui.editor.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.editor.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.sidebar.panels.graph_properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.area.AreaEditorWindow;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFile;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphDownloadRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphUploadRequest;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class AssetBrowserWindow extends FrameLayout implements AreaEditorWindow, AssetBrowserCoordinator {
    private final LinearLayout mMainLayout;
    private final AssetNavigationPanel mNavigationPanel;
    private final AssetFileBrowserPanel mBrowserPanel;
    private final GraphPropertiesPanel mPropertiesPanel;
    private final EditorSidebar mPropertiesSidebar;
    private final SidebarLayoutController mSidebarLayout;
    private final LocalAssetService mLocalAssetService = new LocalAssetService();
    private final AssetTaskController mIoTasks;
    private final Set<Integer> mRemoteRequestIds = new HashSet<>();
    private final EditorSessionState.AssetBrowserState mSessionState;
    private final Runnable mSessionChanged;
    private boolean mRestoringLocation;
    private String mPendingRemoteRestore;

    public AssetBrowserWindow(Context context) {
        this(context, new EditorSessionState.AssetBrowserState(), null);
    }

    public AssetBrowserWindow(
            Context context,
            EditorSessionState.AssetBrowserState sessionState,
            Runnable sessionChanged) {
        super(context);
        mSessionState = sessionState == null
                ? new EditorSessionState.AssetBrowserState()
                : sessionState;
        mSessionChanged = sessionChanged;
        mIoTasks = new AssetTaskController(this);

        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mNavigationPanel = new AssetNavigationPanel(context, this);
        float navigationWeight = sanitizeNavigationWeight(mSessionState.navigationWeight);
        mMainLayout.addView(mNavigationPanel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, navigationWeight));

        mMainLayout.addView(ResizableDivider.weighted(
                context, ResizableDivider.Orientation.HORIZONTAL, delta -> captureNavigationWeight()));

        AppConfig.AssetBrowserConfig browserConfig = ConfigManager.INSTANCE.getConfig().assetBrowser;
        float sidebarWeight = browserConfig.rightSidebarWeight;

        LinearLayout browserWorkspace = new LinearLayout(context);
        browserWorkspace.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.addView(browserWorkspace, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.0f - navigationWeight));

        mBrowserPanel = new AssetFileBrowserPanel(context, this);
        browserWorkspace.addView(mBrowserPanel, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.8f - sidebarWeight));

        View propertiesDivider = ResizableDivider.weighted(context, ResizableDivider.Orientation.HORIZONTAL);
        browserWorkspace.addView(propertiesDivider);

        mPropertiesSidebar = new EditorSidebar(context);
        mPropertiesSidebar.installRegisteredPanels(new SidebarPanelContext(
                context,
                SidebarPanelScope.ASSET_BROWSER));
        mPropertiesPanel = mPropertiesSidebar.requirePanel(
                GraphPropertiesPanel.PANEL_ID,
                GraphPropertiesPanel.class);
        mPropertiesSidebar.restoreSelectedPanel(browserConfig.rightSidebarTab);
        browserWorkspace.addView(mPropertiesSidebar, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                sidebarWeight));

        mSidebarLayout = new SidebarLayoutController(
                browserWorkspace,
                mBrowserPanel,
                propertiesDivider,
                mPropertiesSidebar,
                sidebarWeight,
                (visible, weight) -> ConfigManager.INSTANCE.update(config -> {
                    config.assetBrowser.rightSidebarVisible = visible;
                    config.assetBrowser.rightSidebarWeight = weight;
                    config.assetBrowser.rightSidebarTab = mPropertiesSidebar.getSelectedPanelId();
                }));
        mPropertiesSidebar.setOnCollapseRequested(() -> mSidebarLayout.setVisible(false, true));
        mPropertiesSidebar.setOnSelectedPanelChanged(id -> ConfigManager.INSTANCE.update(
                config -> config.assetBrowser.rightSidebarTab = id));
        mBrowserPanel.setSelectionChangedListener(entries -> mPropertiesPanel.bind(
                AssetFilePropertiesTarget.fromSelection(entries, mBrowserPanel::refreshFileList)));
        mBrowserPanel.setLocationChangedListener(this::captureLocationState);
        mSidebarLayout.initialize(browserConfig.rightSidebarVisible);

        restoreInitialLocation();
        requestRemoteCapabilities();
    }

    /**
     * 跨区协调总线：将导航栏选中的目录分发给主文件浏览区
     */
    @Override
    public void dispatchNavigateTo(File directory) {
        if (mBrowserPanel != null) {
            mBrowserPanel.navigateTo(directory);
        }
    }

    @Override
    public void dispatchNavigateToFavorites() {
        if (mBrowserPanel != null) {
            mBrowserPanel.navigateToFavorites();
        }
    }

    @Override
    public void dispatchNavigateToRemoteRoot() {
        if (mBrowserPanel != null
                && mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowserPanel.navigateToRemoteRoot();
        }
    }

    @Override
    public boolean canBrowseRemote() {
        return mBrowserPanel != null
                && mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE);
    }

    @Override
    public void showUploadDialog(List<File> selectedFiles) {
        if (selectedFiles == null || selectedFiles.isEmpty() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.UPLOAD)) return;
        FolderPickerDialog dialog = FolderPickerDialog.remote(
                getContext(),
                "上传到服务器",
                "",
                targetDirectory -> preflightUpload(selectedFiles, targetDirectory)
        );
        dialog.show(this);
    }

    @Override
    public void showDownloadDialog(List<AssetEntry> remoteEntries) {
        if (remoteEntries == null || remoteEntries.isEmpty() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.DOWNLOAD)) return;
        FolderPickerDialog dialog = FolderPickerDialog.local(
                getContext(),
                "下载到本地",
                AssetBrowserPathPolicy.getLocalDraftsDir(),
                targetDirectory -> startDownload(remoteEntries, targetDirectory)
        );
        dialog.show(this);
    }

    /**
     * 跨区协调总线：当主文件浏览区通过 NavBar 的 "+" 添加快速路径后，刷新导航栏
     */
    @Override
    public void notifySidebarChanged() {
        if (mNavigationPanel != null) {
            mNavigationPanel.buildSidebar();
        }
        requestRemoteCapabilities();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        mSidebarLayout.onOwnerShown();
        if (mBrowserPanel != null) {
            mBrowserPanel.activatePanel();
        }
        if (mBrowserPanel != null) {
            mBrowserPanel.refreshFileList();
        }
        if (mNavigationPanel != null) {
            mNavigationPanel.buildSidebar();
        }
    }

    @Override
    public void onHide() {
        mSidebarLayout.onOwnerHidden();
        mSidebarLayout.persistState();
        if (mBrowserPanel != null) {
            mBrowserPanel.deactivatePanel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mPropertiesPanel.commitPendingEdits();
        mIoTasks.cancelAll();
        cancelRemoteRequests();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyBinding saveBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.GLOBAL_SAVE));
            if (saveBinding != null && saveBinding.matches(event)) {
                mPropertiesPanel.commitPendingEdits();
                return true;
            }

            if (findFocus() instanceof EditText) return super.dispatchKeyEvent(event);
            if (mBrowserPanel != null && mBrowserPanel.handleShortcut(event)) return true;
            KeyBinding sidebarBinding = KeyBinding.parse(ConfigManager.INSTANCE.get(BuiltinConfigEntries.VIEWPORT_TOGGLE_SIDEBAR));
            if (sidebarBinding != null && sidebarBinding.matches(event)) {
                mSidebarLayout.toggle();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private void requestRemoteCapabilities() {
        int requestId = beginRemoteRequest();
        RemoteGraphClientState.onCapabilities(requestId, response -> {
            finishRemoteRequest(requestId);
            post(() -> {
                if (mNavigationPanel != null) {
                    mNavigationPanel.buildSidebar();
                }
                restorePendingRemoteLocation();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphCapabilitiesRequest(requestId));
    }

    private void restoreInitialLocation() {
        mRestoringLocation = true;
        String location = mSessionState.location == null ? "LOCAL" : mSessionState.location;
        if ("FAVORITES".equals(location)) {
            dispatchNavigateToFavorites();
            mRestoringLocation = false;
            return;
        }
        if ("REMOTE".equals(location)) {
            mPendingRemoteRestore = mSessionState.remotePath == null ? "" : mSessionState.remotePath;
            dispatchNavigateTo(AssetBrowserPathPolicy.getLocalDraftsDir());
            return;
        }

        File restored = AssetBrowserPathPolicy.resolveConfigPath(mSessionState.localPath);
        dispatchNavigateTo(restored != null && restored.isDirectory()
                ? restored
                : AssetBrowserPathPolicy.getLocalDraftsDir());
        mRestoringLocation = false;
        captureLocationState();
    }

    private void restorePendingRemoteLocation() {
        if (mPendingRemoteRestore == null) {
            return;
        }
        String remotePath = mPendingRemoteRestore;
        mPendingRemoteRestore = null;
        if (mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.BROWSE)) {
            mBrowserPanel.navigateToRemote(remotePath);
            mRestoringLocation = false;
            return;
        }
        mRestoringLocation = false;
        captureLocationState();
    }

    private void captureLocationState() {
        if (mRestoringLocation || mBrowserPanel == null) {
            return;
        }
        if (mBrowserPanel.isFavoritesMode()) {
            mSessionState.location = "FAVORITES";
        } else if (mBrowserPanel.getSourceKind() == AssetSourceKind.REMOTE) {
            mSessionState.location = "REMOTE";
            mSessionState.remotePath = mBrowserPanel.getRemoteDirectory();
        } else {
            mSessionState.location = "LOCAL";
            mSessionState.localPath = AssetBrowserPathPolicy.toConfigPath(mBrowserPanel.getCurrentDirectory());
        }
        if (mSessionChanged != null) {
            mSessionChanged.run();
        }
    }

    private void captureNavigationWeight() {
        if (mNavigationPanel.getLayoutParams() instanceof LinearLayout.LayoutParams params) {
            mSessionState.navigationWeight = sanitizeNavigationWeight(params.weight);
            if (mSessionChanged != null) {
                mSessionChanged.run();
            }
        }
    }

    private static float sanitizeNavigationWeight(float weight) {
        return Float.isFinite(weight) ? Math.max(0.05f, Math.min(0.45f, weight)) : 0.2f;
    }

    private void preflightUpload(List<File> selectedFiles, String targetDirectory) {
        List<File> selectedSnapshot = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        mIoTasks.run("准备上传",
                context -> mLocalAssetService.collectUploadFiles(selectedSnapshot, targetDirectory, context),
                (result, progress) -> {
                    if (result.files().isEmpty()) {
                        progress.fail(result.failedPaths().isEmpty()
                                ? "没有可上传的资产"
                                : "文件读取失败: " + summarizePaths(result.failedPaths()));
                        return;
                    }
                    if (!result.failedPaths().isEmpty()) {
                        System.err.println("[AssetBrowser] Some upload files failed to read: " + result.failedPaths());
                    }
                    progress.update("准备完成", result.files().size(), result.files().size());
                    requestUploadPreflight(result.files());
                });
    }

    private void requestUploadPreflight(List<RemoteAssetFile> files) {
        int requestId = beginRemoteRequest();
        RemoteGraphClientState.onUpload(requestId, response -> {
            if (response.terminal()) finishRemoteRequest(requestId);
            post(() -> {
                if (!response.preflight()) return;
                if (!response.success() && response.conflicts().isEmpty()) {
                    showUploadFailureDialog(files, false, List.of());
                    return;
                }
                if (response.conflicts().isEmpty()) {
                    startUpload(files, false);
                    return;
                }
                List<String> conflictPaths = new ArrayList<>();
                for (RemoteGraphConflict conflict : response.conflicts()) {
                    conflictPaths.add(conflict.targetPath());
                }
                ConflictResolutionState<RemoteAssetFile> resolution =
                        new ConflictResolutionState<>(files, conflictPaths, RemoteAssetFile::targetPath);
                new OverwriteConfirmDialog(getContext(), conflictPaths, decision -> {
                    switch (decision) {
                        case OVERWRITE_CURRENT -> {
                            if (resolution.markCurrentOverwrite()) {
                                startUpload(resolution.remainingFiles(), false, resolution.overwritePaths());
                            }
                        }
                        case OVERWRITE_ALL -> startUpload(files, true, List.of());
                        case SKIP_CURRENT -> {
                            if (resolution.markCurrentSkip()) {
                                startUpload(resolution.remainingFiles(), false, resolution.overwritePaths());
                            }
                        }
                        case SKIP_ALL -> startUpload(resolution.withoutConflicts(), false, List.of());
                        case CANCEL -> {
                        }
                    }
                }).show(this);
            });
        });
        List<RemoteAssetFile> metadata = files.stream()
                .map(file -> new RemoteAssetFile(file.targetPath(), new byte[0]))
                .toList();
        NetworkHandler.sendToServer(new PacketRemoteGraphUploadRequest(requestId, true, false, metadata));
    }

    private void startUpload(List<RemoteAssetFile> files, boolean overwrite) {
        startUpload(files, overwrite, List.of());
    }

    private void startUpload(List<RemoteAssetFile> files, boolean overwrite, List<String> overwritePaths) {
        if (files.isEmpty()) return;
        UploadBatchRunner runner = new UploadBatchRunner(files, overwrite, overwritePaths);
        runner.sendNext();
    }

    private void startDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (remoteEntries.isEmpty() || targetDirectory == null) return;
        requestDownload(remoteEntries, targetDirectory);
    }

    private void requestDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (remoteEntries.isEmpty()) return;
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : remoteEntries) {
            paths.add(entry.path());
        }

        int requestId = beginRemoteRequest();
        List<RemoteAssetFile> downloaded = new ArrayList<>();
        RemoteGraphClientState.onDownload(requestId, response -> {
            if (response.terminal()) finishRemoteRequest(requestId);
            post(() -> {
                if (!response.success()) {
                    TransferProgressDialog progress = new TransferProgressDialog(getContext(), "下载资产");
                    progress.show(this);
                    progress.fail(response.message());
                    return;
                }
                if (!response.files().isEmpty()) {
                    downloaded.addAll(response.files());
                }
                if (response.terminal()) {
                    finishDownload(downloaded, targetDirectory);
                }
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphDownloadRequest(requestId, paths));
    }

    private void finishDownload(List<RemoteAssetFile> files, File targetDirectory) {
        List<String> conflicts = findLocalDownloadConflicts(files, targetDirectory);
        if (conflicts.isEmpty()) {
            saveDownloadedFiles(files, targetDirectory);
            return;
        }

        ConflictResolutionState<RemoteAssetFile> resolution =
                new ConflictResolutionState<>(files, conflicts, file -> AssetPathUtils.normalizeRemoteFilePath(file.targetPath()));
        new OverwriteConfirmDialog(getContext(), conflicts, decision -> {
            switch (decision) {
                case OVERWRITE_CURRENT -> {
                    if (resolution.markCurrentOverwrite()) {
                        saveDownloadedFiles(resolution.remainingFiles(), targetDirectory);
                    }
                }
                case OVERWRITE_ALL -> saveDownloadedFiles(files, targetDirectory);
                case SKIP_CURRENT -> {
                    if (resolution.markCurrentSkip()) {
                        saveDownloadedFiles(resolution.remainingFiles(), targetDirectory);
                    }
                }
                case SKIP_ALL -> saveDownloadedFiles(resolution.withoutConflicts(), targetDirectory);
                case CANCEL -> {
                }
            }
        }).show(this);
    }

    private void saveDownloadedFiles(List<RemoteAssetFile> files, File targetDirectory) {
        List<RemoteAssetFile> fileSnapshot = files == null ? List.of() : List.copyOf(files);
        mIoTasks.run("保存下载",
                context -> mLocalAssetService.saveDownloadedFiles(fileSnapshot, targetDirectory, context),
                (result, progress) -> {
                    DocumentManager.INSTANCE.refreshFileReferences();
                    ImageThumbnailView.clearCache();
                    if (mBrowserPanel != null) {
                        mBrowserPanel.refreshFileList();
                    }
                    if (!result.failedPaths().isEmpty()) {
                        progress.fail("部分文件保存失败: " + summarizePaths(result.failedPaths()));
                        return;
                    }
                    progress.update("下载完成", result.successCount(), Math.max(1, fileSnapshot.size()));
                });
    }

    private void showUploadFailureDialog(
            List<RemoteAssetFile> failedFiles,
            boolean overwrite,
            List<String> overwritePaths
    ) {
        if (failedFiles == null || failedFiles.isEmpty()) return;
        List<String> failedPaths = new ArrayList<>();
        for (RemoteAssetFile file : failedFiles) {
            failedPaths.add(file.targetPath());
        }
        new UploadFailureRetryDialog(
                getContext(),
                failedPaths,
                () -> startUpload(failedFiles, overwrite, overwritePaths)
        ).show(this);
    }

    private String summarizePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";
        int limit = Math.min(3, paths.size());
        List<String> head = paths.subList(0, limit);
        String summary = String.join(", ", head);
        if (paths.size() > limit) {
            summary += " 等 " + paths.size() + " 项";
        }
        return summary;
    }

    private List<String> findLocalDownloadConflicts(List<RemoteAssetFile> files, File targetDirectory) {
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RemoteAssetFile file : files) {
            String path = AssetPathUtils.normalizeRemoteFilePath(file.targetPath());
            if (!seen.add(path)) continue;
            File target = new File(targetDirectory, path);
            if (target.exists()) {
                conflicts.add(path);
            }
        }
        return conflicts;
    }

    private static final class ConflictResolutionState<T> {
        private final List<T> mFiles;
        private final List<String> mConflicts;
        private final Function<T, String> mPathProvider;
        private final Set<String> mOverwritePaths = new LinkedHashSet<>();
        private final Set<String> mSkipPaths = new LinkedHashSet<>();
        private final Set<String> mConflictSet;
        private int mIndex = 0;

        private ConflictResolutionState(List<T> files, List<String> conflicts, Function<T, String> pathProvider) {
            mFiles = files;
            mConflicts = conflicts;
            mPathProvider = pathProvider;
            mConflictSet = new HashSet<>(conflicts);
        }

        boolean markCurrentOverwrite() {
            if (mIndex < mConflicts.size()) {
                mOverwritePaths.add(mConflicts.get(mIndex));
            }
            return advance();
        }

        boolean markCurrentSkip() {
            if (mIndex < mConflicts.size()) {
                mSkipPaths.add(mConflicts.get(mIndex));
            }
            return advance();
        }

        private boolean advance() {
            mIndex++;
            return mIndex >= mConflicts.size();
        }

        List<String> overwritePaths() {
            return new ArrayList<>(mOverwritePaths);
        }

        List<T> remainingFiles() {
            List<T> result = new ArrayList<>();
            for (T file : mFiles) {
                if (!mSkipPaths.contains(mPathProvider.apply(file))) {
                    result.add(file);
                }
            }
            return result;
        }

        List<T> withoutConflicts() {
            List<T> result = new ArrayList<>();
            for (T file : mFiles) {
                if (!mConflictSet.contains(mPathProvider.apply(file))) {
                    result.add(file);
                }
            }
            return result;
        }
    }

    private final class UploadBatchRunner {
        private final List<RemoteAssetFile> mFiles;
        private final boolean mOverwriteAll;
        private final Set<String> mOverwritePaths;
        private final List<RemoteAssetFile> mFailedFiles = new ArrayList<>();
        private int mIndex = 0;

        private UploadBatchRunner(
                List<RemoteAssetFile> files,
                boolean overwriteAll,
                List<String> overwritePaths
        ) {
            mFiles = files;
            mOverwriteAll = overwriteAll;
            mOverwritePaths = new HashSet<>(overwritePaths);
        }

        void sendNext() {
            if (mIndex >= mFiles.size()) {
                if (mBrowserPanel != null) {
                    mBrowserPanel.refreshFileList();
                }
                if (!mFailedFiles.isEmpty()) {
                    showUploadFailureDialog(mFailedFiles, mOverwriteAll, new ArrayList<>(mOverwritePaths));
                }
                return;
            }

            RemoteAssetFile file = mFiles.get(mIndex);
            int requestId = beginRemoteRequest();
            RemoteGraphClientState.onUpload(requestId, response -> {
                if (response.terminal()) finishRemoteRequest(requestId);
                post(() -> {
                    if (!response.success()) {
                        mFailedFiles.add(file);
                        mIndex++;
                        sendNext();
                        return;
                    }
                    if (response.terminal()) {
                        mIndex++;
                        sendNext();
                    }
                });
            });
            boolean overwrite = mOverwriteAll || mOverwritePaths.contains(file.targetPath());
            NetworkHandler.sendToServer(new PacketRemoteGraphUploadRequest(
                    requestId,
                    false,
                    overwrite,
                    overwrite ? List.of(file.targetPath()) : List.of(),
                    List.of(file)
            ));
        }
    }

    private int beginRemoteRequest() {
        int requestId = RemoteGraphClientState.nextRequestId();
        mRemoteRequestIds.add(requestId);
        return requestId;
    }

    private void finishRemoteRequest(int requestId) {
        mRemoteRequestIds.remove(requestId);
    }

    private void cancelRemoteRequests() {
        for (int requestId : mRemoteRequestIds) {
            RemoteGraphClientState.cancel(requestId);
        }
        mRemoteRequestIds.clear();
    }
}
