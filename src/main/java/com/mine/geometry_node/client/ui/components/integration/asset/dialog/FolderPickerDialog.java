package com.mine.geometry_node.client.ui.components.integration.asset.dialog;

import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerDialog;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerOptions;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
import icyllis.modernui.core.Context;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Compatibility adapter preserving File callbacks locally and relative path callbacks remotely. */
public class FolderPickerDialog extends AssetPickerDialog {
    public enum Source { LOCAL, REMOTE }

    public static FolderPickerDialog local(Context context, String title, File initialDirectory, Consumer<File> onPick) {
        if (initialDirectory != null && !initialDirectory.exists()) initialDirectory.mkdirs();
        AssetLocation initial = initialDirectory == null ? null : new AssetLocation.Local(initialDirectory, false, List.of());
        return new FolderPickerDialog(context, title,
                new AssetPickerOptions(AssetPickerOptions.Sources.LOCAL, AssetPickerOptions.Target.FOLDER, null, initial),
                entry -> { if (onPick != null) onPick.accept(entry.localFile()); });
    }

    public static FolderPickerDialog remote(Context context, String title, String initialDirectory, Consumer<String> onPick) {
        return new FolderPickerDialog(context, title,
                new AssetPickerOptions(AssetPickerOptions.Sources.REMOTE, AssetPickerOptions.Target.FOLDER, null,
                        new AssetLocation.Remote(initialDirectory, false)),
                entry -> { if (onPick != null) onPick.accept(entry.path()); });
    }

    private FolderPickerDialog(Context context, String title, AssetPickerOptions options,
                               Consumer<AssetEntry> onPick) {
        super(context, title, options, onPick);
    }
}
