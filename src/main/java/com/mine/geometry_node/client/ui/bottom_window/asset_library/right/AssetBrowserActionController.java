package com.mine.geometry_node.client.ui.bottom_window.asset_library.right;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.ConfirmDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.GraphTagDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog.TransferProgressDialog;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.service.GraphAssetService;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.service.LocalAssetService;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.task.AssetTaskController;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphFileOperationRequest;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

final class AssetBrowserActionController {
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;

    private final RightFileBrowserPanel mPanel;
    private final AssetBrowserCoordinator mCoordinator;
    private final LocalAssetService mLocalAssetService;
    private final GraphAssetService mGraphAssetService;
    private final GraphFavoriteStore mFavoriteStore;
    private final AssetEntryLoader mEntryLoader;
    private final AssetTaskController mIoTasks;
    private final boolean mEnableLocalFileActions;
    private final boolean mEnableRemoteTransferActions;
    private final boolean mOpenLocalJsonOnDoubleClick;
    private final boolean mShowPickerContextActions;

    private List<File> mClipboardFiles = new ArrayList<>();
    private boolean mIsCutOperation = false;

    private static List<String> sRemoteClipboardPaths = new ArrayList<>();
    private static boolean sRemoteCutOperation = false;

    AssetBrowserActionController(
            RightFileBrowserPanel panel,
            AssetBrowserCoordinator coordinator,
            LocalAssetService localAssetService,
            GraphAssetService graphAssetService,
            GraphFavoriteStore favoriteStore,
            AssetEntryLoader entryLoader,
            AssetTaskController ioTasks,
            boolean enableLocalFileActions,
            boolean enableRemoteTransferActions,
            boolean openLocalJsonOnDoubleClick,
            boolean showPickerContextActions
    ) {
        mPanel = panel;
        mCoordinator = coordinator;
        mLocalAssetService = localAssetService;
        mGraphAssetService = graphAssetService;
        mFavoriteStore = favoriteStore;
        mEntryLoader = entryLoader;
        mIoTasks = ioTasks;
        mEnableLocalFileActions = enableLocalFileActions;
        mEnableRemoteTransferActions = enableRemoteTransferActions;
        mOpenLocalJsonOnDoubleClick = openLocalJsonOnDoubleClick;
        mShowPickerContextActions = showPickerContextActions;
    }

    boolean canCopySelection() {
        List<AssetEntry> entries = mPanel.getSelectedEntries();
        if (entries.isEmpty()) return false;
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions && !mPanel.getSelectedLocalFiles().isEmpty();
        }
        return mEnableRemoteTransferActions && RemoteGraphClientState.canManage()
                && entries.stream().anyMatch(entry -> entry.sourceKind() == AssetSourceKind.REMOTE);
    }

    boolean canPasteClipboard() {
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions && !mPanel.isFavoritesMode() && !mClipboardFiles.isEmpty();
        }
        return mEnableRemoteTransferActions && RemoteGraphClientState.canManage() && !sRemoteClipboardPaths.isEmpty();
    }

    void copySelectionToClipboard() {
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            List<File> files = mPanel.getSelectedLocalFiles();
            if (files.isEmpty()) return;
            mClipboardFiles = new ArrayList<>(files);
            mIsCutOperation = false;
            return;
        }

        if (!mEnableRemoteTransferActions || !RemoteGraphClientState.canManage()) return;
        List<AssetEntry> entries = mPanel.getSelectedEntries();
        if (entries.isEmpty()) return;
        setRemoteClipboard(entries, false);
    }

    void pasteClipboard() {
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            performPaste();
        } else {
            pasteRemoteEntries();
        }
    }

    void showContextMenuAtRaw(float rawX, float rawY, AssetEntry targetEntry) {
        int[] loc = new int[2];
        mPanel.bodyFrame().getLocationOnScreen(loc);
        showContextMenu(rawX - loc[0], rawY - loc[1], targetEntry);
    }

    boolean moveLocalEntries(List<AssetEntry> entries, AssetEntry targetDirectoryEntry) {
        if (targetDirectoryEntry.sourceKind() != AssetSourceKind.LOCAL || targetDirectoryEntry.localFile() == null) return false;
        File targetDirectory = targetDirectoryEntry.localFile();
        if (!targetDirectory.isDirectory()) return false;

        List<File> sourceFiles = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.LOCAL && entry.localFile() != null) {
                sourceFiles.add(entry.localFile());
            }
        }
        if (sourceFiles.isEmpty()) return false;

        mIoTasks.run("移动文件",
                context -> mLocalAssetService.moveFilesToDirectory(sourceFiles, targetDirectory, context),
                (result, progress) -> {
                    for (LocalAssetService.FileMove move : result.movedFiles()) {
                        mFavoriteStore.updatePath(move.source(), move.destination());
                    }
                    if (!result.movedFiles().isEmpty()) {
                        mPanel.clearSelection();
                        mPanel.refreshFileList();
                    }
                    finishFileOperation(progress, "移动完成", result.movedFiles().size(), result.failedPaths());
                });
        return true;
    }

    boolean moveRemoteEntries(List<AssetEntry> entries, AssetEntry targetDirectoryEntry) {
        if (targetDirectoryEntry.sourceKind() != AssetSourceKind.REMOTE || !targetDirectoryEntry.isDirectory()) return false;
        if (!mEnableRemoteTransferActions || !RemoteGraphClientState.canManage()) return false;

        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE && !entry.path().equals(targetDirectoryEntry.path())) {
                paths.add(entry.path());
            }
        }
        if (paths.isEmpty()) return false;

        sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation.MOVE, paths, targetDirectoryEntry.path());
        return true;
    }

    void triggerNewItem(boolean isFolder) {
        if (mPanel.isFavoritesMode()) return;
        mPanel.clearSearch();
        File currentDirectory = mPanel.getCurrentDirectory();
        if (currentDirectory == null) return;

        String defaultName = isFolder ? "新建文件夹" : "新建文件.json";
        mIoTasks.run(isFolder ? "新建文件夹" : "新建文件",
                context -> mLocalAssetService.createAssetItem(currentDirectory, defaultName, isFolder, context),
                (result, progress) -> {
                    File newFile = result.file();
                    AssetEntry newEntry = mEntryLoader.toLocalEntry(newFile, currentDirectory, false);
                    mPanel.refreshFileList(() -> {
                        mPanel.selectOnly(newEntry);
                        startInlineEdit(newFile);
                        progress.dismiss();
                    });
                });
    }

    void handleDoubleClick(AssetEntry entry) {
        Consumer<AssetEntry> pickFileAction = mPanel.pickFileAction();
        if (pickFileAction != null && !entry.isDirectory()) {
            pickFileAction.accept(entry);
            return;
        }

        if (entry.sourceKind() == AssetSourceKind.REMOTE) {
            if (entry.isDirectory()) {
                mPanel.navigateToRemote(entry.path());
            }
            return;
        }

        File file = entry.localFile();
        if (file == null) return;
        if (file.isDirectory()) {
            mPanel.navigateTo(file);
        } else if (mOpenLocalJsonOnDoubleClick && file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            openGraphFile(file);
        }
    }

    private void addLocalContextActions(FileContextMenu menu, List<File> filesSnapshot) {
        if (filesSnapshot.isEmpty()) return;
        String suffix = filesSnapshot.size() > 1 ? " (" + filesSnapshot.size() + ")" : "";
        List<File> uploadCandidates = getUploadCandidates(filesSnapshot);
        if (mEnableRemoteTransferActions && mCoordinator != null && RemoteGraphClientState.canUpload() && !uploadCandidates.isEmpty()) {
            String uploadSuffix = uploadCandidates.size() > 1 ? " (" + uploadCandidates.size() + ")" : "";
            menu.addMenuItem("上传到服务器" + uploadSuffix, () -> mCoordinator.showUploadDialog(uploadCandidates));
            menu.addDivider();
        }
        if (!mEnableLocalFileActions) return;
        if (filesSnapshot.size() == 1 && isLocalGraphFile(filesSnapshot.get(0))) {
            menu.addMenuItem("编辑标签", () -> showGraphTagDialog(filesSnapshot.get(0)));
            menu.addDivider();
        }
        menu.addMenuItem("复制" + suffix, shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
        menu.addMenuItem("剪切" + suffix, () -> {
            mClipboardFiles = new ArrayList<>(filesSnapshot);
            mIsCutOperation = true;
        });
        menu.addMenuItem("删除" + suffix, () -> deleteLocalFiles(filesSnapshot));
        if (filesSnapshot.size() == 1) {
            menu.addDivider();
            menu.addMenuItem("重命名", () -> startInlineEdit(filesSnapshot.get(0)));
        }
        menu.addDivider();
    }

    private void deleteLocalFiles(List<File> filesSnapshot) {
        if (filesSnapshot == null || filesSnapshot.isEmpty()) return;
        List<File> files = new ArrayList<>(filesSnapshot);
        mIoTasks.run("删除文件",
                context -> mLocalAssetService.deleteFiles(files, context),
                (result, progress) -> {
                    for (File file : result.successfulFiles()) {
                        mFavoriteStore.removePath(file);
                    }
                    if (!result.successfulFiles().isEmpty()) {
                        mPanel.clearSelection();
                        mPanel.refreshFileList();
                    }
                    finishFileOperation(progress, "删除完成", result.successfulFiles().size(), result.failedPaths());
                });
    }

    private boolean isLocalGraphFile(File file) {
        return AssetEntryLoader.isLocalGraphFile(file);
    }

    private List<File> getUploadCandidates(List<File> files) {
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.exists()) continue;
            if (file.isDirectory() || isLocalGraphFile(file)) {
                result.add(file);
            }
        }
        return result;
    }

    private void showGraphTagDialog(File file) {
        GraphTagDialog dialog = new GraphTagDialog(mPanel.getContext(), file, tags -> {
            syncOpenGraphTags(file, tags);
            mPanel.refreshFileList();
        });
        dialog.showIn(mPanel);
    }

    private void syncOpenGraphTags(File file, List<String> tags) {
        String targetKey = mPanel.pathKey(file);
        for (GraphSession session : DocumentManager.INSTANCE.getSessions()) {
            if (session == null || session.editorContext == null || session.editorContext.getGraph() == null) continue;
            File sessionFile = new File(session.fileId);
            if (!targetKey.equals(mPanel.pathKey(sessionFile))) continue;
            session.editorContext.getGraph().tags = new ArrayList<>(tags);
        }
    }

    private void addRemoteContextActions(FileContextMenu menu, List<AssetEntry> entriesSnapshot) {
        if (entriesSnapshot.isEmpty() || !mEnableRemoteTransferActions) return;
        String suffix = entriesSnapshot.size() > 1 ? " (" + entriesSnapshot.size() + ")" : "";
        if (mCoordinator != null && RemoteGraphClientState.canDownload()) {
            menu.addMenuItem("下载到本地" + suffix, () -> mCoordinator.showDownloadDialog(entriesSnapshot));
            menu.addDivider();
        }
        if (RemoteGraphClientState.canManage()) {
            menu.addMenuItem("复制" + suffix, shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
            menu.addMenuItem("剪切" + suffix, () -> setRemoteClipboard(entriesSnapshot, true));
            menu.addMenuItem("删除" + suffix, () -> deleteRemoteEntries(entriesSnapshot));
            menu.addDivider();
        }
    }

    private void showContextMenu(float localX, float localY, AssetEntry targetEntry) {
        FileContextMenu menu = new FileContextMenu(mPanel.getContext());

        if (targetEntry != null && !mPanel.selectedPathsSnapshot().contains(targetEntry.key())) {
            mPanel.selectOnly(targetEntry);
        }

        List<AssetEntry> actionEntries = targetEntry == null ? Collections.emptyList() : mPanel.getSelectedEntries();
        if (!actionEntries.isEmpty()) {
            if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
                addLocalContextActions(menu, mPanel.getSelectedLocalFiles());
            } else {
                addRemoteContextActions(menu, new ArrayList<>(actionEntries));
            }
        }

        if (mEnableLocalFileActions && !mPanel.isFavoritesMode()
                && mPanel.getSourceKind() == AssetSourceKind.LOCAL && !mClipboardFiles.isEmpty()) {
            menu.addMenuItem("粘贴", shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableRemoteTransferActions && mPanel.getSourceKind() == AssetSourceKind.REMOTE
                && RemoteGraphClientState.canManage() && !sRemoteClipboardPaths.isEmpty()) {
            menu.addMenuItem(sRemoteCutOperation ? "移动到此处" : "粘贴", shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableLocalFileActions && !mPanel.isFavoritesMode() && mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
            menu.addMenuItem("新建文件", () -> triggerNewItem(false));
        }

        Consumer<AssetEntry> pickFileAction = mPanel.pickFileAction();
        if (mShowPickerContextActions && pickFileAction != null && targetEntry != null && !targetEntry.isDirectory()) {
            menu.addMenuItem("选择文件", () -> pickFileAction.accept(targetEntry));
        }
        Runnable pickCurrentDirectoryAction = mPanel.pickCurrentDirectoryAction();
        if (mShowPickerContextActions && pickCurrentDirectoryAction != null) {
            menu.addMenuItem("选择当前文件夹", pickCurrentDirectoryAction);
        }
        if (mShowPickerContextActions) {
            menu.addMenuItem("刷新", mPanel::refreshFileList);
        }

        if (menu.hasItems()) {
            menu.showAt(localX, localY, mPanel.bodyFrame());
        }
    }

    private void startInlineEdit(File targetFile) {
        String key = mPanel.pathKey(targetFile);
        AssetFileItemView itemView = mPanel.itemViews().get(key);
        if (itemView == null) {
            int top = mPanel.fileContent().entryTop(key);
            if (top < 0) return;
            mPanel.scrollView().scrollTo(0, Math.max(0, top - dp2pxInt(8)));
            mPanel.updateVirtualViewport();
            mPanel.fileContent().forceRefreshMountedItems();
            itemView = mPanel.itemViews().get(key);
        }
        if (itemView == null) return;

        TextView originalTextView = itemView.getNameView();
        ViewGroup parent = (ViewGroup) originalTextView.getParent();
        int index = parent.indexOfChild(originalTextView);
        originalTextView.setVisibility(View.GONE);

        EditText editInput = new EditText(mPanel.getContext());
        editInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp2px(TEXT_SIZE_LIST_ITEM));
        editInput.setTextColor(0xFFFFFFFF);
        editInput.setBackground(RightFileBrowserPanel.createColorDrawable(0xFF444444));
        editInput.setSingleLine(true);
        editInput.setPadding(dp2pxInt(6), 0, dp2pxInt(6), 0);
        editInput.setText(targetFile.getName());

        parent.addView(editInput, index, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        editInput.requestFocus();
        String name = editInput.getText().toString();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex > 0 && !targetFile.isDirectory()) {
            editInput.setSelection(0, dotIndex);
        } else {
            editInput.setSelection(name.length());
        }

        Runnable commitAction = new Runnable() {
            boolean isCommitted = false;

            @Override
            public void run() {
                if (isCommitted) return;
                isCommitted = true;

                String newName = editInput.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(targetFile.getName())) {
                    renameFile(targetFile, newName);
                    return;
                }
                mPanel.refreshFileList();
            }
        };

        editInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEY_ENTER) {
                commitAction.run();
                return true;
            }
            return false;
        });

        editInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitAction.run();
        });
    }

    private void renameFile(File targetFile, String newName) {
        mPanel.refreshFileList();
        mIoTasks.run("重命名文件",
                context -> mLocalAssetService.renameFile(targetFile, newName, context),
                (result, progress) -> {
                    if (result.renamed()) {
                        mFavoriteStore.updatePath(result.source(), result.destination());
                    }
                    mPanel.refreshFileList();
                    progress.dismiss();
                });
    }

    private void performPaste() {
        File currentDirectory = mPanel.getCurrentDirectory();
        if (mPanel.isFavoritesMode() || mClipboardFiles.isEmpty() || currentDirectory == null) return;
        List<File> clipboardSnapshot = new ArrayList<>(mClipboardFiles);
        boolean cutOperation = mIsCutOperation;
        mIoTasks.run(cutOperation ? "移动文件" : "复制文件",
                context -> mLocalAssetService.pasteFiles(clipboardSnapshot, currentDirectory, cutOperation, context),
                (result, progress) -> {
                    for (LocalAssetService.FileMove move : result.movedFiles()) {
                        mFavoriteStore.updatePath(move.source(), move.destination());
                    }
                    if (cutOperation && !result.movedFiles().isEmpty()) {
                        mClipboardFiles.clear();
                    }
                    if (!result.successfulFiles().isEmpty()) {
                        mPanel.clearSelection();
                        mPanel.refreshFileList();
                    }
                    finishFileOperation(progress,
                            cutOperation ? "移动完成" : "复制完成",
                            result.successfulFiles().size(),
                            result.failedPaths());
                });
    }

    private void setRemoteClipboard(List<AssetEntry> entries, boolean cutOperation) {
        sRemoteClipboardPaths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                sRemoteClipboardPaths.add(entry.path());
            }
        }
        sRemoteCutOperation = cutOperation;
    }

    private String shortcutText(AssetLibraryActionId actionId) {
        return AssetLibraryActionRegistry.shortcutText(actionId, ConfigManager.INSTANCE.getConfig());
    }

    private void deleteRemoteEntries(List<AssetEntry> entries) {
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                paths.add(entry.path());
            }
        }
        if (paths.isEmpty()) return;
        String message = paths.size() == 1
                ? "确定删除云端项目: " + entries.get(0).name()
                : "确定删除选中的 " + paths.size() + " 个云端项目？";
        ConfirmDialog dialog = new ConfirmDialog(
                mPanel.getContext(),
                "删除云端文件",
                message,
                "删除",
                () -> sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation.DELETE, paths, "")
        );
        dialog.showIn(mPanel);
    }

    private void pasteRemoteEntries() {
        if (sRemoteClipboardPaths.isEmpty()) return;
        PacketRemoteGraphFileOperationRequest.Operation operation = sRemoteCutOperation
                ? PacketRemoteGraphFileOperationRequest.Operation.MOVE
                : PacketRemoteGraphFileOperationRequest.Operation.COPY;
        sendRemoteFileOperation(operation, new ArrayList<>(sRemoteClipboardPaths), mPanel.getRemoteDirectory());
    }

    private void sendRemoteFileOperation(PacketRemoteGraphFileOperationRequest.Operation operation, List<String> paths, String targetDirectory) {
        if (paths.isEmpty()) return;
        int requestId = RemoteGraphClientState.nextRequestId();
        String title = switch (operation) {
            case DELETE -> "删除云端文件";
            case COPY -> "复制云端文件";
            case MOVE -> "移动云端文件";
        };
        TransferProgressDialog progress = new TransferProgressDialog(mPanel.getContext(), title);
        progress.showIn(mPanel);
        RemoteGraphClientState.onFileOperation(requestId, response -> {
            mPanel.post(() -> {
                if (!response.success()) {
                    progress.fail(response.message());
                    System.err.println("[AssetBrowser] Remote file operation failed: " + response.message());
                    return;
                }
                progress.update(response.message(), 1, 1);
                if (operation == PacketRemoteGraphFileOperationRequest.Operation.MOVE) {
                    sRemoteClipboardPaths = new ArrayList<>();
                    sRemoteCutOperation = false;
                }
                mPanel.clearSelection();
                mPanel.refreshFileList();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteGraphFileOperationRequest(requestId, operation, targetDirectory, paths));
    }

    private void finishFileOperation(
            TransferProgressDialog progress,
            String successMessage,
            int successCount,
            List<String> failedPaths
    ) {
        if (failedPaths != null && !failedPaths.isEmpty()) {
            progress.fail("部分文件处理失败: " + summarizePaths(failedPaths));
            return;
        }
        progress.update(successMessage, successCount, Math.max(1, successCount));
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

    private void openGraphFile(File file) {
        mIoTasks.run("打开图纸",
                context -> mGraphAssetService.loadGraphSession(file, context),
                (session, progress) -> {
                    DocumentManager.INSTANCE.openSession(session);
                    progress.dismiss();
                });
    }
}
