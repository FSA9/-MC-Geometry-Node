package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerDialog;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerOptions;
import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetLocation;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Compatibility adapter for path-valued callers. New callers should use AssetPickerDialog. */
public class FilePickerDialog extends AssetPickerDialog {
    interface CapabilityRequests extends AssetPickerDialog.CapabilityRequests { }

    public static FilePickerDialog path(Context context, String title, String initialPath, Consumer<String> onPick) {
        return new FilePickerDialog(context, title, initialPath, onPick, REMOTE_CAPABILITIES);
    }

    public static FilePickerDialog schematic(Context context, String title, File initialDirectory, Consumer<String> onPick) {
        return path(context, title, initialDirectory == null ? "" : initialDirectory.getAbsolutePath(), onPick);
    }

    public static FilePickerDialog showPath(View anchor, String initialPath, Consumer<String> onPick) {
        if (anchor == null) return null;
        FilePickerDialog dialog = path(anchor.getContext(), "选择路径", initialPath, onPick);
        dialog.show(anchor);
        return dialog;
    }

    public static FilePickerDialog showSchematic(View anchor, File initialDirectory, Consumer<String> onPick) {
        return showPath(anchor, initialDirectory == null ? "" : initialDirectory.getAbsolutePath(), onPick);
    }

    FilePickerDialog(Context context, String title, String initialPath, Consumer<String> onPick,
                     CapabilityRequests capabilityRequests) {
        this(context, title, initialPath, onPick, (AssetPickerDialog.CapabilityRequests) capabilityRequests);
    }

    private FilePickerDialog(Context context, String title, String initialPath, Consumer<String> onPick,
                             AssetPickerDialog.CapabilityRequests capabilityRequests) {
        super(context, title, options(initialPath), entry -> {
            if (onPick != null) onPick.accept(toPath(entry));
        }, capabilityRequests);
    }

    private static AssetPickerOptions options(String initialPath) {
        String remote = initialRemoteDirectory(initialPath);
        AssetLocation initial = remote != null ? new AssetLocation.Remote(remote, false)
                : new AssetLocation.Local(initialLocalDirectory(initialPath), false, List.of());
        return new AssetPickerOptions(AssetPickerOptions.Sources.BOTH, AssetPickerOptions.Target.FILE_OR_FOLDER,
                entry -> entry.supports(AssetTypeAction.PICK), initial);
    }

    private static String toPath(AssetEntry entry) {
        if (entry.sourceKind() == AssetSourceKind.REMOTE) return AssetPathUtils.formatRemotePath(entry.path());
        File file = entry.localFile();
        try { return file.getCanonicalPath(); }
        catch (Exception ignored) { return file.getAbsolutePath(); }
    }

    private static File initialLocalDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim();
        if (!value.isEmpty()) {
            File direct = AssetBrowserPathPolicy.resolveConfigPath(value);
            File directDirectory = directoryOfExistingPath(direct);
            if (directDirectory != null) {
                return directDirectory;
            }
        }
        return AssetBrowserPathPolicy.getLocalDraftsDir();
    }

    private static String initialRemoteDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim().replace('\\', '/');
        if (value.isEmpty()) return null;
        if (AssetPathUtils.isRemotePathInput(value)) {
            return remoteDirectoryFromPath(AssetPathUtils.remotePathFromInput(value));
        }
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:/.*")) {
            return null;
        }
        File local = AssetBrowserPathPolicy.resolveConfigPath(value);
        if (directoryOfExistingPath(local) != null) {
            return null;
        }
        return remoteDirectoryFromPath(value);
    }

    private static String remoteDirectoryFromPath(String path) {
        String normalized = AssetPathUtils.remotePathFromInput(path);
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (!name.contains(".")) {
            return normalized;
        }
        return parentRemoteDirectory(normalized);
    }

    private static String parentRemoteDirectory(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash > 0 ? normalized.substring(0, slash) : "";
    }

    private static File directoryOfExistingPath(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        return file.isDirectory() ? file : file.getParentFile();
    }

}
