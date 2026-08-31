package com.mine.geometry_node.client.ui.editor.asset;

import com.mine.geometry_node.client.ui.editor.asset.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.editor.asset.dialog.OverwriteConfirmDialog;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferPlanState;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.navigation.AssetNavigationPanel;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.ui.editor.asset.properties.AssetFilePropertiesTarget;
import com.mine.geometry_node.client.ui.editor.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.editor.asset.service.LocalAssetService;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.components.common.ResizableDivider;
import com.mine.geometry_node.client.ui.components.sidebar.EditorSidebar;
import com.mine.geometry_node.client.ui.components.sidebar.SidebarLayoutController;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.config.KeyBinding;
import com.mine.geometry_node.client.ui.persistence.session.EditorSessionState;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetConflict;
import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.AssetTransferPlanKind;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesRequest;
import com.mine.geometry_node.core.node.document.NodeGraph;
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
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
    private final Set<Integer> mTransferPlanRequestIds = new HashSet<>();
    private final EditorSessionState.AssetBrowserState mSessionState;
    private final Runnable mSessionChanged;
    private final Consumer<GraphSession> mSessionSavedListener;
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
        mSessionSavedListener = session -> post(() ->
                mBrowserPanel.refreshIfDisplayingLocalFile(session.filePath()));
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

    @Override
    public void createRemoteGraph(String targetPath, Runnable onSuccess) {
        if (targetPath == null || targetPath.isBlank() || mBrowserPanel == null
                || !mBrowserPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.UPLOAD)) return;
        mIoTasks.runSilent(context -> {
            Path temporary = Files.createTempFile("geometry-node-new-graph-", ".json");
            try {
                Files.writeString(temporary, GraphJsonIO.toJson(new NodeGraph()), StandardCharsets.UTF_8);
                return temporary;
            } catch (Exception exception) {
                Files.deleteIfExists(temporary);
                throw exception;
            }
        }, temporary -> {
            java.util.UUID jobId;
            try {
                jobId = ClientAssetTransferService.INSTANCE.submit(List.of(ClientAssetTransferRequest.upload(
                        temporary, targetPath, AssetTransferConflictPolicy.FAIL_IF_EXISTS)));
            } catch (RuntimeException exception) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (java.io.IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                System.err.println("[AssetBrowser] Failed to submit generated graph: " + exception.getMessage());
                return;
            }
            ClientAssetTransferService.INSTANCE.completion(jobId).thenAccept(result -> {
                try {
                    Files.deleteIfExists(temporary);
                } catch (java.io.IOException exception) {
                    System.err.println("[AssetBrowser] Failed to remove generated graph temporary file: "
                            + temporary);
                }
                if (result.completedFileCount() == result.files().size()) {
                    post(() -> {
                        if (mBrowserPanel != null) mBrowserPanel.refreshFileList(onSuccess);
                    });
                }
            });
        }, exception -> System.err.println("[AssetBrowser] Failed to create remote graph: "
                + exception.getMessage()));
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
        DocumentManager.INSTANCE.addOnSessionSavedListener(mSessionSavedListener);
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
        DocumentManager.INSTANCE.removeOnSessionSavedListener(mSessionSavedListener);
        mSidebarLayout.onOwnerHidden();
        mSidebarLayout.persistState();
        if (mBrowserPanel != null) {
            mBrowserPanel.deactivatePanel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        DocumentManager.INSTANCE.removeOnSessionSavedListener(mSessionSavedListener);
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
        RemoteAssetClient.onCapabilities(requestId, response -> {
            finishRemoteRequest(requestId);
            post(() -> {
                if (mNavigationPanel != null) {
                    mNavigationPanel.buildSidebar();
                }
                restorePendingRemoteLocation();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteAssetCapabilitiesRequest(requestId));
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
        mIoTasks.runSilent(
                context -> mLocalAssetService.collectUploadSources(selectedSnapshot, targetDirectory, context),
                result -> {
                    if (result.files().isEmpty()) {
                        System.err.println("[AssetBrowser] No transferable upload sources: " + result.failedPaths());
                        return;
                    }
                    if (!result.failedPaths().isEmpty()) {
                        System.err.println("[AssetBrowser] Some upload sources could not be scanned: " + result.failedPaths());
                    }
                    requestUploadPreflight(result.files());
                }, exception -> {
                    System.err.println("[AssetBrowser] Upload scan failed: " + exception.getMessage());
                    exception.printStackTrace();
                });
    }

    private void requestUploadPreflight(List<LocalAssetService.UploadSource> files) {
        int[] requestHolder = new int[1];
        int requestId = ClientAssetTransferPlanState.request(AssetTransferPlanKind.UPLOAD_CONFLICTS,
                files.stream().map(LocalAssetService.UploadSource::targetPath).toList(), response -> {
            mTransferPlanRequestIds.remove(requestHolder[0]);
            post(() -> {
                if (!response.success()) {
                    System.err.println("[AssetBrowser] Upload planning failed: " + response.message());
                    return;
                }
                if (response.conflicts().isEmpty()) {
                    startUpload(files, false);
                    return;
                }
                List<String> conflictPaths = new ArrayList<>();
                for (RemoteAssetConflict conflict : response.conflicts()) {
                    conflictPaths.add(conflict.targetPath());
                }
                ConflictResolutionState<LocalAssetService.UploadSource> resolution =
                        new ConflictResolutionState<>(files, conflictPaths, LocalAssetService.UploadSource::targetPath);
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
        requestHolder[0] = requestId;
        mTransferPlanRequestIds.add(requestId);
    }

    private void startUpload(List<LocalAssetService.UploadSource> files, boolean overwrite) {
        startUpload(files, overwrite, List.of());
    }

    private void startUpload(List<LocalAssetService.UploadSource> files, boolean overwrite, List<String> overwritePaths) {
        if (files.isEmpty()) return;
        Set<String> overwriteSet = new HashSet<>(overwritePaths);
        List<ClientAssetTransferRequest> requests = files.stream().map(file -> ClientAssetTransferRequest.upload(
                file.sourcePath(), file.targetPath(), overwrite || overwriteSet.contains(file.targetPath())
                        ? AssetTransferConflictPolicy.OVERWRITE : AssetTransferConflictPolicy.FAIL_IF_EXISTS)).toList();
        java.util.UUID jobId = ClientAssetTransferService.INSTANCE.submit(requests);
        ClientAssetTransferService.INSTANCE.completion(jobId).thenRun(() -> post(() -> {
            if (mBrowserPanel != null) mBrowserPanel.refreshFileList();
        }));
    }

    private void startDownload(List<AssetEntry> remoteEntries, File targetDirectory) {
        if (remoteEntries.isEmpty() || targetDirectory == null) return;
        List<String> paths = remoteEntries.stream().map(AssetEntry::path).toList();
        int[] requestHolder = new int[1];
        int requestId = ClientAssetTransferPlanState.request(AssetTransferPlanKind.DOWNLOAD_MANIFEST, paths, response -> {
            mTransferPlanRequestIds.remove(requestHolder[0]);
            post(() -> {
                if (!response.success()) {
                    System.err.println("[AssetBrowser] Download planning failed: " + response.message());
                    return;
                }
                finishDownload(response.files(), targetDirectory);
            });
        });
        requestHolder[0] = requestId;
        mTransferPlanRequestIds.add(requestId);
    }

    private void finishDownload(List<AssetDescriptor> files, File targetDirectory) {
        List<String> conflicts = findLocalDownloadConflicts(files, targetDirectory);
        if (conflicts.isEmpty()) {
            submitDownload(files, targetDirectory, false, List.of());
            return;
        }

        ConflictResolutionState<AssetDescriptor> resolution =
                new ConflictResolutionState<>(files, conflicts, file -> AssetPathUtils.normalizeRemoteFilePath(file.path()));
        new OverwriteConfirmDialog(getContext(), conflicts, decision -> {
            switch (decision) {
                case OVERWRITE_CURRENT -> {
                    if (resolution.markCurrentOverwrite()) {
                        submitDownload(resolution.remainingFiles(), targetDirectory, false, resolution.overwritePaths());
                    }
                }
                case OVERWRITE_ALL -> submitDownload(files, targetDirectory, true, List.of());
                case SKIP_CURRENT -> {
                    if (resolution.markCurrentSkip()) {
                        submitDownload(resolution.remainingFiles(), targetDirectory, false, resolution.overwritePaths());
                    }
                }
                case SKIP_ALL -> submitDownload(resolution.withoutConflicts(), targetDirectory, false, List.of());
                case CANCEL -> {
                }
            }
        }).show(this);
    }

    private void submitDownload(List<AssetDescriptor> files, File targetDirectory,
                                boolean overwrite, List<String> overwritePaths) {
        if (files.isEmpty()) return;
        Path root = targetDirectory.toPath().toAbsolutePath().normalize();
        Set<String> overwriteSet = new HashSet<>(overwritePaths);
        List<ClientAssetTransferRequest> requests = new ArrayList<>();
        for (AssetDescriptor file : files) {
            String remotePath = AssetPathUtils.normalizeRemoteFilePath(file.path());
            Path target = root.resolve(remotePath).normalize();
            if (!target.startsWith(root)) continue;
            requests.add(ClientAssetTransferRequest.download(remotePath, target,
                    overwrite || overwriteSet.contains(remotePath)
                            ? AssetTransferConflictPolicy.OVERWRITE : AssetTransferConflictPolicy.FAIL_IF_EXISTS));
        }
        if (requests.isEmpty()) return;
        java.util.UUID jobId = ClientAssetTransferService.INSTANCE.submit(requests);
        ClientAssetTransferService.INSTANCE.completion(jobId).thenRun(() -> post(() -> {
            DocumentManager.INSTANCE.refreshFileReferences();
            ImageThumbnailView.clearCache();
            if (mBrowserPanel != null) mBrowserPanel.refreshFileList();
        }));
    }

    private List<String> findLocalDownloadConflicts(List<AssetDescriptor> files, File targetDirectory) {
        List<String> conflicts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AssetDescriptor file : files) {
            String path = AssetPathUtils.normalizeRemoteFilePath(file.path());
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

    private int beginRemoteRequest() {
        int requestId = RemoteAssetClient.nextRequestId();
        mRemoteRequestIds.add(requestId);
        return requestId;
    }

    private void finishRemoteRequest(int requestId) {
        mRemoteRequestIds.remove(requestId);
    }

    private void cancelRemoteRequests() {
        for (int requestId : mRemoteRequestIds) {
            RemoteAssetClient.cancel(requestId);
        }
        mRemoteRequestIds.clear();
        for (int requestId : mTransferPlanRequestIds) ClientAssetTransferPlanState.cancel(requestId);
        mTransferPlanRequestIds.clear();
    }
}
