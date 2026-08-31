package com.mine.geometry_node.client.ui.editor.asset.browser;

import com.mine.geometry_node.client.ui.editor.asset.AssetBrowserCoordinator;
import com.mine.geometry_node.client.ui.editor.asset.action.AssetLibraryActionId;
import com.mine.geometry_node.client.ui.editor.asset.action.AssetLibraryActionRegistry;
import com.mine.geometry_node.client.ui.editor.asset.dialog.ConfirmDialog;
import com.mine.geometry_node.client.ui.editor.asset.dialog.TransferProgressDialog;
import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.menu.FileContextMenu;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetRepositoryOperation;
import com.mine.geometry_node.client.ui.editor.asset.repository.LocalAssetRepository;
import com.mine.geometry_node.client.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.asset.file.AssetSystemFileBrowser;
import com.mine.geometry_node.client.ui.editor.asset.schematic.SchematicThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.service.GraphAssetService;
import com.mine.geometry_node.client.ui.editor.asset.service.LocalAssetService;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileReference;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileRegistry;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationRequest;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2px;
import static com.mine.geometry_node.client.ui.utils.UIUtils.dp2pxInt;

final class AssetBrowserActionController {
    private static final float TEXT_SIZE_LIST_ITEM = 14.0f;

    private final AssetFileBrowserPanel mPanel;
    private final AssetBrowserCoordinator mCoordinator;
    private final LocalAssetService mLocalAssetService;
    private final GraphAssetService mGraphAssetService;
    private final AssetFavoriteStore mFavoriteStore;
    private final AssetTaskController mIoTasks;
    private final boolean mEnableLocalFileActions;
    private final boolean mEnableRemoteTransferActions;
    private final boolean mOpenLocalJsonOnDoubleClick;
    private final boolean mShowPickerContextActions;

    private final AssetBrowserClipboard mClipboard = new AssetBrowserClipboard();

    private final Set<Integer> mRemoteRequestIds = new HashSet<>();

    AssetBrowserActionController(
            AssetFileBrowserPanel panel,
            AssetBrowserCoordinator coordinator,
            LocalAssetService localAssetService,
            GraphAssetService graphAssetService,
            AssetFavoriteStore favoriteStore,
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
        mIoTasks = ioTasks;
        mEnableLocalFileActions = enableLocalFileActions;
        mEnableRemoteTransferActions = enableRemoteTransferActions;
        mOpenLocalJsonOnDoubleClick = openLocalJsonOnDoubleClick;
        mShowPickerContextActions = showPickerContextActions;
    }

    boolean canCopySelection() {
        return canActOnSelection(AssetTypeAction.COPY);
    }

    boolean canPasteClipboard() {
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions && !mPanel.isFavoritesMode() && !mClipboard.localFiles().isEmpty();
        }
        return mEnableRemoteTransferActions
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                && !mClipboard.remotePaths().isEmpty();
    }

    boolean canCutSelection() {
        return canActOnSelection(AssetTypeAction.MOVE);
    }

    boolean canDeleteSelection() {
        return canActOnSelection(AssetTypeAction.DELETE);
    }

    boolean canRenameSelection() {
        List<AssetEntry> entries = mPanel.getSelectedEntries();
        if (entries.size() != 1 || !entries.getFirst().supports(AssetTypeAction.RENAME)) return false;
        return switch (mPanel.getSourceKind()) {
            case LOCAL -> mEnableLocalFileActions
                    && mPanel.repositorySupports(AssetSourceKind.LOCAL, AssetRepositoryOperation.MANAGE);
            case REMOTE -> mEnableRemoteTransferActions
                    && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE);
        };
    }

    private boolean canActOnSelection(AssetTypeAction action) {
        List<AssetEntry> entries = mPanel.getSelectedEntries();
        if (entries.isEmpty() || entries.stream().anyMatch(entry -> !entry.supports(action))) return false;
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            return mEnableLocalFileActions
                    && mPanel.repositorySupports(AssetSourceKind.LOCAL, AssetRepositoryOperation.MANAGE)
                    && entries.stream().allMatch(entry -> entry.sourceKind() == AssetSourceKind.LOCAL
                    && entry.localFile() != null);
        }
        return mEnableRemoteTransferActions
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                && entries.stream().allMatch(entry -> entry.sourceKind() == AssetSourceKind.REMOTE);
    }

    void copySelectionToClipboard() {
        if (!canCopySelection()) return;
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            List<File> files = mPanel.getSelectedLocalFiles();
            if (files.isEmpty()) return;
            mClipboard.setLocal(files, false);
            return;
        }

        if (!mEnableRemoteTransferActions
                || !mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)) return;
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

    void cutSelectionToClipboard() {
        if (!canCutSelection()) return;
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            List<File> files = mPanel.getSelectedLocalFiles();
            if (files.isEmpty()) return;
            mClipboard.setLocal(files, true);
            return;
        }

        if (!mEnableRemoteTransferActions
                || !mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)) return;
        List<AssetEntry> entries = mPanel.getSelectedEntries();
        if (entries.isEmpty()) return;
        setRemoteClipboard(entries, true);
    }

    void deleteSelection() {
        if (!canDeleteSelection()) return;
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            confirmDeleteLocalFiles(mPanel.getSelectedLocalFiles());
        } else {
            deleteRemoteEntries(mPanel.getSelectedEntries());
        }
    }

    void renameSelection() {
        if (!canRenameSelection()) return;
        startInlineEdit(mPanel.getSelectedEntries().getFirst());
    }

    void showContextMenuAtRaw(float rawX, float rawY, AssetEntry targetEntry) {
        int[] loc = new int[2];
        mPanel.bodyFrame().getLocationOnScreen(loc);
        showContextMenu(rawX - loc[0], rawY - loc[1], targetEntry);
    }

    boolean moveLocalEntries(List<AssetEntry> entries, AssetEntry targetDirectoryEntry) {
        if (!mEnableLocalFileActions
                || !mPanel.repositorySupports(AssetSourceKind.LOCAL, AssetRepositoryOperation.MANAGE)
                || entries == null || entries.isEmpty()
                || entries.stream().anyMatch(entry -> !entry.supports(AssetTypeAction.MOVE))) return false;
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
                    DocumentManager.INSTANCE.refreshFileReferences();
                    for (LocalAssetService.FileMove move : result.movedFiles()) {
                        ImageThumbnailView.invalidateUnder(move.source());
                        SchematicThumbnailView.invalidateUnder(move.source());
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
        if (!mEnableRemoteTransferActions
                || !mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                || entries == null || entries.isEmpty()
                || entries.stream().anyMatch(entry -> !entry.supports(AssetTypeAction.MOVE))) return false;

        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE && !entry.path().equals(targetDirectoryEntry.path())) {
                paths.add(entry.path());
            }
        }
        if (paths.isEmpty()) return false;

        sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation.MOVE, paths, targetDirectoryEntry.path());
        return true;
    }

    void triggerNewItem(boolean isFolder) {
        if (mPanel.isFavoritesMode()
                || !mPanel.repositorySupports(mPanel.getSourceKind(), AssetRepositoryOperation.CREATE)) return;
        mPanel.clearSearch();
        if (mPanel.getSourceKind() == AssetSourceKind.REMOTE) {
            String name = availableName(isFolder ? "新建文件夹" : "新建文件.json", isFolder);
            String path = joinRemotePath(mPanel.getRemoteDirectory(), name);
            if (isFolder) {
                sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation.CREATE_DIRECTORY,
                        List.of(), path);
            } else if (mCoordinator != null) {
                mCoordinator.createRemoteGraph(path, () -> selectRemotePath(path));
            }
            return;
        }
        File currentDirectory = mPanel.getCurrentDirectory();
        if (currentDirectory == null) return;

        String defaultName = isFolder ? "新建文件夹" : "新建文件.json";
        String initialContent = isFolder ? null : GraphJsonIO.toJson(new NodeGraph());
        mIoTasks.run(isFolder ? "新建文件夹" : "新建文件",
                context -> mLocalAssetService.createAssetItem(
                        currentDirectory, defaultName, isFolder, initialContent, context),
                (result, progress) -> {
                    File newFile = result.file();
                    AssetEntry newEntry = AssetEntry.local(newFile,
                            LocalAssetRepository.pathKey(newFile), newFile.getName());
                    mPanel.refreshFileList(() -> {
                        mPanel.selectOnly(newEntry);
                        startInlineEdit(newEntry);
                        progress.requestClose();
                    });
                });
    }

    void createRemoteDirectory(String path, boolean navigateAfterCreation) {
        if (mPanel.getSourceKind() != AssetSourceKind.REMOTE
                || !mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.CREATE)) return;
        sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation.CREATE_DIRECTORY,
                List.of(), path, navigateAfterCreation ? () -> mPanel.navigateToRemote(path) : null);
    }

    void handleDoubleClick(AssetEntry entry) {
        Consumer<AssetEntry> pickFileAction = mPanel.pickFileAction();
        if (pickFileAction != null && !entry.isDirectory() && entry.supports(AssetTypeAction.PICK)) {
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
        } else if (mOpenLocalJsonOnDoubleClick && entry.supports(AssetTypeAction.OPEN)) {
            openGraphFile(file);
        }
    }

    private void addLocalContextActions(FileContextMenu menu, List<AssetEntry> entriesSnapshot) {
        List<File> filesSnapshot = entriesSnapshot.stream()
                .filter(entry -> entry.sourceKind() == AssetSourceKind.LOCAL && entry.localFile() != null)
                .map(AssetEntry::localFile)
                .toList();
        if (filesSnapshot.isEmpty()) return;
        String suffix = filesSnapshot.size() > 1 ? " (" + filesSnapshot.size() + ")" : "";
        List<File> uploadCandidates = entriesSnapshot.stream()
                .filter(entry -> entry.sourceKind() == AssetSourceKind.LOCAL
                        && entry.localFile() != null
                        && entry.supports(AssetTypeAction.UPLOAD))
                .map(AssetEntry::localFile)
                .toList();
        if (mEnableRemoteTransferActions && mCoordinator != null
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.UPLOAD)
                && !uploadCandidates.isEmpty()) {
            String uploadSuffix = uploadCandidates.size() > 1 ? " (" + uploadCandidates.size() + ")" : "";
            menu.addMenuItem("上传到服务器" + uploadSuffix, () -> mCoordinator.showUploadDialog(uploadCandidates));
            menu.addDivider();
        }
        if (!mEnableLocalFileActions) return;
        boolean added = false;
        if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.COPY))) {
            menu.addMenuItem(actionLabel(AssetLibraryActionId.COPY) + suffix,
                    shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
            added = true;
        }
        if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.MOVE))) {
            menu.addMenuItem(actionLabel(AssetLibraryActionId.CUT) + suffix,
                    shortcutText(AssetLibraryActionId.CUT), () -> {
                    mClipboard.setLocal(filesSnapshot, true);
            });
            added = true;
        }
        if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.DELETE))) {
            menu.addMenuItem(actionLabel(AssetLibraryActionId.DELETE) + suffix,
                    shortcutText(AssetLibraryActionId.DELETE), () -> confirmDeleteLocalFiles(filesSnapshot));
            added = true;
        }
        if (entriesSnapshot.size() == 1 && entriesSnapshot.get(0).supports(AssetTypeAction.RENAME)) {
            if (added) menu.addDivider();
            menu.addMenuItem(actionLabel(AssetLibraryActionId.RENAME),
                    shortcutText(AssetLibraryActionId.RENAME), () -> startInlineEdit(entriesSnapshot.getFirst()));
            added = true;
        }
        if (entriesSnapshot.size() == 1) {
            if (added) menu.addDivider();
            File target = filesSnapshot.getFirst();
            menu.addMenuItem("在资源管理器中打开", () -> openInSystemFileBrowser(target));
            added = true;
        }
        if (added) menu.addDivider();
    }

    private void confirmDeleteLocalFiles(List<File> filesSnapshot) {
        if (filesSnapshot == null || filesSnapshot.isEmpty()) return;
        List<File> files = new ArrayList<>(filesSnapshot);
        String message = files.size() == 1
                ? "确定永久删除本地项目: " + files.get(0).getName()
                : "确定永久删除选中的 " + files.size() + " 个本地项目？";
        ConfirmDialog dialog = new ConfirmDialog(
                mPanel.getContext(),
                "删除本地文件",
                message,
                actionLabel(AssetLibraryActionId.DELETE),
                () -> deleteLocalFiles(files)
        );
        dialog.show(mPanel);
    }

    private void deleteLocalFiles(List<File> files) {
        List<File> deletionTargets = files == null ? new ArrayList<>() : new ArrayList<>(files);
        deletionTargets.removeIf(file -> file == null);
        if (deletionTargets.isEmpty()) return;

        DocumentManager.INSTANCE.closeSessionsUnder(
                deletionTargets.stream().map(File::toPath).toList());
        runLocalDeleteTask(deletionTargets);
    }

    private void runLocalDeleteTask(List<File> files) {
        mIoTasks.run("删除文件",
                context -> mLocalAssetService.deleteFiles(files, context),
                (result, progress) -> {
                    DocumentManager.INSTANCE.refreshFileReferences();
                    for (File file : result.successfulFiles()) {
                        ImageThumbnailView.invalidateUnder(file);
                        SchematicThumbnailView.invalidateUnder(file);
                        mFavoriteStore.removePath(file);
                    }
                    if (!result.successfulFiles().isEmpty()) {
                        mPanel.clearSelection();
                        mPanel.refreshFileList();
                    }
                    finishFileOperation(progress, "删除完成", result.successfulFiles().size(), result.failedPaths());
                });
    }

    private void addRemoteContextActions(FileContextMenu menu, List<AssetEntry> entriesSnapshot) {
        if (entriesSnapshot.isEmpty() || !mEnableRemoteTransferActions) return;
        String suffix = entriesSnapshot.size() > 1 ? " (" + entriesSnapshot.size() + ")" : "";
        if (mCoordinator != null
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.DOWNLOAD)
                && entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.DOWNLOAD))) {
            menu.addMenuItem("下载到本地" + suffix, () -> mCoordinator.showDownloadDialog(entriesSnapshot));
            menu.addDivider();
        }
        if (mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)) {
            boolean added = false;
            if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.COPY))) {
                menu.addMenuItem(actionLabel(AssetLibraryActionId.COPY) + suffix,
                        shortcutText(AssetLibraryActionId.COPY), this::copySelectionToClipboard);
                added = true;
            }
            if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.MOVE))) {
                menu.addMenuItem(actionLabel(AssetLibraryActionId.CUT) + suffix,
                        shortcutText(AssetLibraryActionId.CUT), () -> setRemoteClipboard(entriesSnapshot, true));
                added = true;
            }
            if (entriesSnapshot.stream().allMatch(entry -> entry.supports(AssetTypeAction.DELETE))) {
                menu.addMenuItem(actionLabel(AssetLibraryActionId.DELETE) + suffix,
                        shortcutText(AssetLibraryActionId.DELETE), () -> deleteRemoteEntries(entriesSnapshot));
                added = true;
            }
            if (entriesSnapshot.size() == 1 && entriesSnapshot.getFirst().supports(AssetTypeAction.RENAME)) {
                if (added) menu.addDivider();
                menu.addMenuItem(actionLabel(AssetLibraryActionId.RENAME),
                        shortcutText(AssetLibraryActionId.RENAME), () -> startInlineEdit(entriesSnapshot.getFirst()));
                added = true;
            }
            if (added) menu.addDivider();
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
                addLocalContextActions(menu, new ArrayList<>(actionEntries));
            } else {
                addRemoteContextActions(menu, new ArrayList<>(actionEntries));
            }
        }

        if (mEnableLocalFileActions && !mPanel.isFavoritesMode()
                && mPanel.getSourceKind() == AssetSourceKind.LOCAL && !mClipboard.localFiles().isEmpty()) {
            menu.addMenuItem(actionLabel(AssetLibraryActionId.PASTE),
                    shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableRemoteTransferActions && mPanel.getSourceKind() == AssetSourceKind.REMOTE
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                && !mClipboard.remotePaths().isEmpty()) {
            menu.addMenuItem(mClipboard.isRemoteCut()
                            ? translated("geometry_node.asset_library.action.move_here")
                            : actionLabel(AssetLibraryActionId.PASTE),
                    shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (!mPanel.isFavoritesMode()
                && mPanel.repositorySupports(mPanel.getSourceKind(), AssetRepositoryOperation.CREATE)) {
            menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
            if (mPanel.getSourceKind() == AssetSourceKind.LOCAL || mCoordinator != null) {
                menu.addMenuItem("新建文件", () -> triggerNewItem(false));
            }
        }

        Consumer<AssetEntry> pickFileAction = mPanel.pickFileAction();
        if (mShowPickerContextActions && pickFileAction != null && targetEntry != null
                && !targetEntry.isDirectory() && targetEntry.supports(AssetTypeAction.PICK)) {
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

    private void startInlineEdit(AssetEntry targetEntry) {
        String key = targetEntry.key();
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
        editInput.setBackground(AssetFileBrowserPanel.createColorDrawable(0xFF444444));
        editInput.setSingleLine(true);
        editInput.setPadding(dp2pxInt(6), 0, dp2pxInt(6), 0);
        editInput.setText(targetEntry.name());

        parent.addView(editInput, index, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        editInput.requestFocus();
        String name = editInput.getText().toString();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex > 0 && !targetEntry.isDirectory()) {
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
                if (!newName.isEmpty() && !newName.equals(targetEntry.name())) {
                    if (targetEntry.sourceKind() == AssetSourceKind.LOCAL && targetEntry.localFile() != null) {
                        renameFile(targetEntry.localFile(), newName);
                    } else if (targetEntry.sourceKind() == AssetSourceKind.REMOTE) {
                        renameRemoteEntry(targetEntry, newName);
                    }
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
                    DocumentManager.INSTANCE.refreshFileReferences();
                    if (result.renamed()) {
                        ImageThumbnailView.invalidateUnder(result.source());
                        SchematicThumbnailView.invalidateUnder(result.source());
                        mFavoriteStore.updatePath(result.source(), result.destination());
                    }
                    mPanel.refreshFileList();
                    progress.requestClose();
                });
    }

    private void performPaste() {
        File currentDirectory = mPanel.getCurrentDirectory();
        if (mPanel.isFavoritesMode() || mClipboard.localFiles().isEmpty() || currentDirectory == null) return;
        List<File> clipboardSnapshot = mClipboard.localFiles();
        boolean cutOperation = mClipboard.isLocalCut();
        mIoTasks.run(cutOperation ? "移动文件" : "复制文件",
                context -> mLocalAssetService.pasteFiles(clipboardSnapshot, currentDirectory, cutOperation, context),
                (result, progress) -> {
                    if (cutOperation) {
                        DocumentManager.INSTANCE.refreshFileReferences();
                    }
                    for (LocalAssetService.FileMove move : result.movedFiles()) {
                        ImageThumbnailView.invalidateUnder(move.source());
                        SchematicThumbnailView.invalidateUnder(move.source());
                        mFavoriteStore.updatePath(move.source(), move.destination());
                    }
                    if (cutOperation && !result.movedFiles().isEmpty()) {
                        mClipboard.clearLocal();
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
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                paths.add(entry.path());
            }
        }
        mClipboard.setRemote(paths, cutOperation);
    }

    private String shortcutText(AssetLibraryActionId actionId) {
        return AssetLibraryActionRegistry.shortcutText(actionId, ConfigManager.INSTANCE.getConfig());
    }

    private String actionLabel(AssetLibraryActionId actionId) {
        return AssetLibraryActionRegistry.label(actionId);
    }

    private static String translated(String translationKey) {
        return Component.translatable(translationKey).getString();
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
                actionLabel(AssetLibraryActionId.DELETE),
                () -> sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation.DELETE, paths, "")
        );
        dialog.show(mPanel);
    }

    private void pasteRemoteEntries() {
        List<String> clipboardPaths = mClipboard.remotePaths();
        if (clipboardPaths.isEmpty()) return;
        PacketRemoteAssetFileOperationRequest.Operation operation = mClipboard.isRemoteCut()
                ? PacketRemoteAssetFileOperationRequest.Operation.MOVE
                : PacketRemoteAssetFileOperationRequest.Operation.COPY;
        sendRemoteFileOperation(operation, clipboardPaths, mPanel.getRemoteDirectory());
    }

    private void sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation operation, List<String> paths, String destinationPath) {
        sendRemoteFileOperation(operation, paths, destinationPath, null);
    }

    private void sendRemoteFileOperation(
            PacketRemoteAssetFileOperationRequest.Operation operation,
            List<String> paths,
            String destinationPath,
            Runnable onSuccess
    ) {
        if (paths.isEmpty() && operation != PacketRemoteAssetFileOperationRequest.Operation.CREATE_DIRECTORY) return;
        int requestId = RemoteAssetClient.nextRequestId();
        mRemoteRequestIds.add(requestId);
        String title = switch (operation) {
            case DELETE -> "删除云端文件";
            case COPY -> "复制云端文件";
            case MOVE -> "移动云端文件";
            case CREATE_DIRECTORY -> "新建云端文件夹";
            case RENAME -> "重命名云端文件";
        };
        TransferProgressDialog progress = new TransferProgressDialog(mPanel.getContext(), title);
        progress.show(mPanel);
        RemoteAssetClient.onFileOperation(requestId, response -> {
            mRemoteRequestIds.remove(requestId);
            mPanel.post(() -> {
                if (!response.success()) {
                    progress.fail(response.message());
                    System.err.println("[AssetBrowser] Remote file operation failed: " + response.message());
                    return;
                }
                progress.update(response.message(), 1, 1);
                if (operation == PacketRemoteAssetFileOperationRequest.Operation.MOVE) {
                    mClipboard.clearRemote();
                }
                mPanel.clearSelection();
                if (onSuccess != null) {
                    onSuccess.run();
                    return;
                }
                mPanel.refreshFileList(() -> {
                    if (operation == PacketRemoteAssetFileOperationRequest.Operation.CREATE_DIRECTORY) {
                        selectRemotePath(destinationPath);
                    }
                });
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteAssetFileOperationRequest(requestId, operation, destinationPath, paths));
    }

    void cancelRemoteRequests() {
        for (int requestId : mRemoteRequestIds) {
            RemoteAssetClient.cancel(requestId);
        }
        mRemoteRequestIds.clear();
    }

    private void renameRemoteEntry(AssetEntry entry, String newName) {
        String parent = remoteParent(entry.path());
        sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation.RENAME,
                List.of(entry.path()), joinRemotePath(parent, newName));
    }

    private void openInSystemFileBrowser(File target) {
        mIoTasks.runSilent(context -> {
            AssetSystemFileBrowser.open(target.toPath());
            return null;
        }, ignored -> { }, exception -> System.err.println(
                "[AssetBrowser] Failed to open system file browser: " + exception.getMessage()));
    }

    private String availableName(String requestedName, boolean directory) {
        Set<String> existing = new HashSet<>();
        for (AssetEntry entry : mPanel.visibleEntriesSnapshot()) {
            existing.add(entry.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (!existing.contains(requestedName.toLowerCase(java.util.Locale.ROOT))) return requestedName;
        String base = requestedName;
        String extension = "";
        if (!directory) {
            int dot = requestedName.lastIndexOf('.');
            if (dot > 0) {
                base = requestedName.substring(0, dot);
                extension = requestedName.substring(dot);
            }
        }
        for (int index = 1; ; index++) {
            String candidate = base + "_" + index + extension;
            if (!existing.contains(candidate.toLowerCase(java.util.Locale.ROOT))) return candidate;
        }
    }

    private void selectRemotePath(String path) {
        for (AssetEntry entry : mPanel.visibleEntriesSnapshot()) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE && entry.path().equals(path)) {
                mPanel.selectOnly(entry);
                startInlineEdit(entry);
                return;
            }
        }
    }

    private static String remoteParent(String path) {
        int separator = path == null ? -1 : path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator);
    }

    private static String joinRemotePath(String directory, String name) {
        return directory == null || directory.isBlank() ? name : directory + "/" + name;
    }

    void clearClipboard() {
        mClipboard.clear();
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
        GraphFileReference reference = GraphFileRegistry.INSTANCE.reference(file.toPath());
        GraphSession existing = DocumentManager.INSTANCE.findSession(reference);
        if (existing != null) {
            DocumentManager.INSTANCE.openSession(existing);
            return;
        }
        mIoTasks.run("打开图纸",
                context -> mGraphAssetService.loadGraphSession(file, context),
                (session, progress) -> {
                    DocumentManager.INSTANCE.openSession(session);
                    progress.requestClose();
                });
    }
}
