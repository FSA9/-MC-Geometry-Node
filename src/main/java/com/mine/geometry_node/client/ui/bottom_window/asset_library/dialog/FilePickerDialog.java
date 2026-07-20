package com.mine.geometry_node.client.ui.bottom_window.asset_library.dialog;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.model.AssetEntry;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.persistence.PathUtils;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FilePickerDialog extends AssetDialogBase {
    private final RightFileBrowserPanel mBrowser;
    private final Predicate<AssetEntry> mEntryFilter;
    private final Consumer<String> mOnPick;
    private final TextView mStatus;

    public static FilePickerDialog schematic(Context context, String title, File initialDirectory, Consumer<String> onPick) {
        return new FilePickerDialog(context, title, initialDirectory, AssetEntry::isSchematicFile, onPick);
    }

    public static FilePickerDialog showSchematic(View anchor, File initialDirectory, Consumer<String> onPick) {
        if (anchor == null) {
            return null;
        }
        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            return null;
        }
        FilePickerDialog dialog = schematic(anchor.getContext(), "选择结构文件", initialDirectory, onPick);
        dialog.showIn(host);
        return dialog;
    }

    private FilePickerDialog(
            Context context,
            String title,
            File initialDirectory,
            Predicate<AssetEntry> fileFilter,
            Consumer<String> onPick
    ) {
        super(context, title);
        mEntryFilter = entry -> entry != null && (entry.isDirectory() || (fileFilter != null && fileFilter.test(entry)));
        mOnPick = onPick;

        mBrowser = new RightFileBrowserPanel(context);
        mBrowser.setEntryFilter(mEntryFilter);
        mBrowser.setPickFileAction(this::chooseEntry);
        mPanel.addView(mBrowser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(360)
        ));

        mStatus = label(context, "", 12, 0xFFFFB86C);
        mPanel.addView(mStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(22)
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        Button cancel = button(context, "取消", 0xFF4A4A4A);
        cancel.setOnClickListener(v -> dismiss());
        Button choose = button(context, "选择文件", 0xFF2F7DDE);
        choose.setOnClickListener(v -> chooseSelectedFile());
        actions.addView(cancel, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(110), UIUtils.dp2pxInt(32));
        chooseLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(choose, chooseLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(36)
        );
        actionsLp.topMargin = UIUtils.dp2pxInt(8);
        mPanel.addView(actions, actionsLp);

        File directory = initialDirectory != null ? initialDirectory : AssetBrowserPathPolicy.getLocalDraftsDir();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        mBrowser.navigateTo(directory);
    }

    @Override
    public void showIn(ViewGroup parent) {
        super.showIn(parent);
    }

    private void chooseSelectedFile() {
        List<AssetEntry> selected = mBrowser.getSelectedEntriesSnapshot();
        for (AssetEntry entry : selected) {
            if (isPickableFile(entry)) {
                chooseEntry(entry);
                return;
            }
        }
        mStatus.setText("请选择一个支持的文件。");
    }

    private void chooseEntry(AssetEntry entry) {
        if (!isPickableFile(entry)) {
            mStatus.setText("请选择一个支持的文件。");
            return;
        }
        if (mOnPick != null) {
            mOnPick.accept(toNodePath(entry.localFile()));
        }
        dismiss();
    }

    private boolean isPickableFile(AssetEntry entry) {
        return entry != null
                && !entry.isDirectory()
                && entry.localFile() != null
                && mEntryFilter.test(entry);
    }

    private static String toNodePath(File file) {
        if (file == null) {
            return "";
        }
        try {
            Path geometryRoot = PathUtils.resolveWorkspacePath("geometry_nodes").toPath().toRealPath();
            Path target = file.toPath().toRealPath();
            if (target.startsWith(geometryRoot)) {
                return normalizeSeparators(geometryRoot.relativize(target).toString());
            }
            return target.toString();
        } catch (Exception ignored) {
            try {
                Path geometryRoot = PathUtils.resolveWorkspacePath("geometry_nodes").toPath().toAbsolutePath().normalize();
                Path target = file.toPath().toAbsolutePath().normalize();
                if (target.startsWith(geometryRoot)) {
                    return normalizeSeparators(geometryRoot.relativize(target).toString());
                }
                return target.toString();
            } catch (Exception ignoredAgain) {
                return file.getPath();
            }
        }
    }

    private static String normalizeSeparators(String path) {
        return path == null ? "" : path.replace(File.separatorChar, '/');
    }

    private static ViewGroup findWindowHost(View anchor) {
        View current = anchor;
        ViewGroup best = anchor instanceof ViewGroup viewGroup ? viewGroup : null;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View parentView)) {
                break;
            }
            current = parentView;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }
}
