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
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteAssetFileOperationRequest;
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

    private List<File> mClipboardFiles = new ArrayList<>();
    private boolean mIsCutOperation = false;

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
            return mEnableLocalFileActions && !mPanel.isFavoritesMode() && !mClipboardFiles.isEmpty();
        }
        return mEnableRemoteTransferActions
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                && !RemoteAssetClient.clipboardPaths().isEmpty();
    }

    boolean canCutSelection() {
        return canActOnSelection(AssetTypeAction.MOVE);
    }

    boolean canDeleteSelection() {
        return canActOnSelection(AssetTypeAction.DELETE);
    }

    boolean canRenameSelection() {
        return mPanel.getSourceKind() == AssetSourceKind.LOCAL
                && mEnableLocalFileActions
                && mPanel.repositorySupports(AssetSourceKind.LOCAL, AssetRepositoryOperation.MANAGE)
                && mPanel.getSelectedEntries().size() == 1
                && mPanel.getSelectedEntries().get(0).supports(AssetTypeAction.RENAME);
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
            mClipboardFiles = new ArrayList<>(files);
            mIsCutOperation = false;
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
            mClipboardFiles = new ArrayList<>(files);
            mIsCutOperation = true;
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
        List<File> files = mPanel.getSelectedLocalFiles();
        if (mPanel.getSourceKind() == AssetSourceKind.LOCAL && files.size() == 1) {
            startInlineEdit(files.get(0));
        }
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
                || !mPanel.repositorySupports(AssetSourceKind.LOCAL, AssetRepositoryOperation.CREATE)) return;
        mPanel.clearSearch();
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
                        startInlineEdit(newFile);
                        progress.requestClose();
                    });
                });
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
                mClipboardFiles = new ArrayList<>(filesSnapshot);
                mIsCutOperation = true;
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
                    shortcutText(AssetLibraryActionId.RENAME), () -> startInlineEdit(filesSnapshot.get(0)));
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
                && mPanel.getSourceKind() == AssetSourceKind.LOCAL && !mClipboardFiles.isEmpty()) {
            menu.addMenuItem(actionLabel(AssetLibraryActionId.PASTE),
                    shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableRemoteTransferActions && mPanel.getSourceKind() == AssetSourceKind.REMOTE
                && mPanel.repositorySupports(AssetSourceKind.REMOTE, AssetRepositoryOperation.MANAGE)
                && !RemoteAssetClient.clipboardPaths().isEmpty()) {
            menu.addMenuItem(RemoteAssetClient.isCutOperation()
                            ? translated("geometry_node.asset_library.action.move_here")
                            : actionLabel(AssetLibraryActionId.PASTE),
                    shortcutText(AssetLibraryActionId.PASTE), this::pasteClipboard);
            menu.addDivider();
        }
        if (mEnableLocalFileActions && !mPanel.isFavoritesMode() && mPanel.getSourceKind() == AssetSourceKind.LOCAL) {
            menu.addMenuItem("新建文件夹", () -> triggerNewItem(true));
            menu.addMenuItem("新建文件", () -> triggerNewItem(false));
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
        editInput.setBackground(AssetFileBrowserPanel.createColorDrawable(0xFF444444));
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
        if (mPanel.isFavoritesMode() || mClipboardFiles.isEmpty() || currentDirectory == null) return;
        List<File> clipboardSnapshot = new ArrayList<>(mClipboardFiles);
        boolean cutOperation = mIsCutOperation;
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
        List<String> paths = new ArrayList<>();
        for (AssetEntry entry : entries) {
            if (entry.sourceKind() == AssetSourceKind.REMOTE) {
                paths.add(entry.path());
            }
        }
        RemoteAssetClient.setClipboard(paths, cutOperation);
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
        List<String> clipboardPaths = RemoteAssetClient.clipboardPaths();
        if (clipboardPaths.isEmpty()) return;
        PacketRemoteAssetFileOperationRequest.Operation operation = RemoteAssetClient.isCutOperation()
                ? PacketRemoteAssetFileOperationRequest.Operation.MOVE
                : PacketRemoteAssetFileOperationRequest.Operation.COPY;
        sendRemoteFileOperation(operation, clipboardPaths, mPanel.getRemoteDirectory());
    }

    private void sendRemoteFileOperation(PacketRemoteAssetFileOperationRequest.Operation operation, List<String> paths, String targetDirectory) {
        if (paths.isEmpty()) return;
        int requestId = RemoteAssetClient.nextRequestId();
        mRemoteRequestIds.add(requestId);
        String title = switch (operation) {
            case DELETE -> "删除云端文件";
            case COPY -> "复制云端文件";
            case MOVE -> "移动云端文件";
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
                    RemoteAssetClient.clearClipboard();
                }
                mPanel.clearSelection();
                mPanel.refreshFileList();
            });
        });
        NetworkHandler.sendToServer(new PacketRemoteAssetFileOperationRequest(requestId, operation, targetDirectory, paths));
    }

    void cancelRemoteRequests() {
        for (int requestId : mRemoteRequestIds) {
            RemoteAssetClient.cancel(requestId);
        }
        mRemoteRequestIds.clear();
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
