package com.mine.geometry_node.client.ui.components.integration.asset.dialog;

import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import com.mine.geometry_node.core.engine.system.asset.reference.AssetReference;
import com.mine.geometry_node.core.engine.system.asset.reference.AssetResolutionContext;
import com.mine.geometry_node.client.platform.ClientPaths;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerDialog;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerOptions;
import com.mine.geometry_node.client.feature.asset.path.AssetPathUtils;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
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
        AssetLocation initial;
        try {
            String remote = initialRemoteDirectory(initialPath);
            initial = remote != null ? new AssetLocation.Remote(remote, false)
                    : new AssetLocation.Local(initialLocalDirectory(initialPath), false, List.of());
        } catch (IllegalArgumentException invalidReference) {
            initial = new AssetLocation.Local(AssetBrowserPathPolicy.getCommonDir(), false, List.of());
        }
        return new AssetPickerOptions(AssetPickerOptions.Sources.BOTH, AssetPickerOptions.Target.FILE_OR_FOLDER,
                entry -> entry.supports(AssetTypeAction.PICK), initial);
    }

    private static String toPath(AssetEntry entry) {
        if (entry.sourceKind() == AssetSourceKind.REMOTE) {
            AssetRepositoryPath address = AssetRepositoryPath.parse(entry.path(), true);
            return address.isCommon() ? "remote:" + address.relativePath() : address.serialize();
        }
        File file = entry.localFile();
        try { return "local:" + file.getCanonicalPath(); }
        catch (Exception ignored) { return "local:" + file.getAbsolutePath(); }
    }

    private static File initialLocalDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim();
        if (!value.isEmpty()) {
            File direct;
            if (value.startsWith("local:")) {
                direct = AssetBrowserPathPolicy.resolveConfigPath(value.substring("local:".length()));
            } else if (value.startsWith("pack:")) {
                try {
                    direct = AssetReference.parse(value).resolvePath(new AssetResolutionContext(
                            ClientPaths.getWorkspaceRoot().toPath(), null)).toFile();
                } catch (IllegalArgumentException invalidReference) {
                    direct = null;
                }
            } else {
                direct = AssetBrowserPathPolicy.resolveConfigPath(value);
            }
            File directDirectory = directoryOfExistingPath(direct);
            if (directDirectory != null) {
                return directDirectory;
            }
        }
        return AssetBrowserPathPolicy.getCommonDir();
    }

    private static String initialRemoteDirectory(String currentPath) {
        String value = currentPath == null ? "" : currentPath.trim().replace('\\', '/');
        if (value.isEmpty()) return null;
        if (value.startsWith("worldpack:") || AssetPathUtils.isRemotePathInput(value)) {
            return remoteDirectoryFromPath(AssetPathUtils.remotePathFromInput(value));
        }
        if (value.startsWith("local:") || value.startsWith("pack:") || value.startsWith("/") || value.matches("^[A-Za-z]:.*") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:/.*")) {
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
        return AssetPathUtils.parentRemoteDirectory(normalized);
    }

    private static File directoryOfExistingPath(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        return file.isDirectory() ? file : file.getParentFile();
    }

}
