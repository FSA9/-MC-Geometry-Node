package com.mine.geometry_node.client.ui.editor.asset.dialog;

import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.function.Consumer;

public class FolderPickerDialog extends AssetDialogBase {
    public enum Source {
        LOCAL,
        REMOTE
    }

    private final AssetFileBrowserPanel mBrowser;
    private final Source mSource;
    private final Consumer<File> mOnLocalPick;
    private final Consumer<String> mOnRemotePick;
    private File mCurrentLocalDirectory;
    private String mCurrentRemoteDirectory = "";

    public static FolderPickerDialog local(Context context, String title, File initialDirectory, Consumer<File> onPick) {
        return new FolderPickerDialog(context, title, Source.LOCAL, initialDirectory, "", onPick, null);
    }

    public static FolderPickerDialog remote(Context context, String title, String initialDirectory, Consumer<String> onPick) {
        return new FolderPickerDialog(context, title, Source.REMOTE, null, initialDirectory, null, onPick);
    }

    private FolderPickerDialog(
            Context context,
            String title,
            Source source,
            File initialLocalDirectory,
            String initialRemoteDirectory,
            Consumer<File> onLocalPick,
            Consumer<String> onRemotePick
    ) {
        super(context, title);
        mSource = source;
        mOnLocalPick = onLocalPick;
        mOnRemotePick = onRemotePick;

        mBrowser = new AssetFileBrowserPanel(context);
        mBrowser.setPickCurrentDirectoryAction(this::chooseCurrentDirectory);
        mPanel.addView(mBrowser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(360)
        ));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.RIGHT);
        UiActionButton create = actionButton(context, tr("geometry_node.folder_picker.create_folder"),
                UiActionButton.Role.QUIET);
        create.setOnClickListener(v -> createFolderInCurrentDirectory());
        UiActionButton cancel = actionButton(context, tr("geometry_node.folder_picker.cancel"),
                UiActionButton.Role.SECONDARY);
        cancel.setOnClickListener(v -> requestClose());
        UiActionButton choose = actionButton(context, tr("geometry_node.folder_picker.select_current"),
                UiActionButton.Role.PRIMARY);
        choose.setOnClickListener(v -> chooseCurrentDirectory());
        actions.addView(create, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(108), UIUtils.dp2pxInt(32)));
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(92), UIUtils.dp2pxInt(32));
        cancelLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(cancel, cancelLp);
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(150), UIUtils.dp2pxInt(32));
        chooseLp.leftMargin = UIUtils.dp2pxInt(8);
        actions.addView(choose, chooseLp);
        setActions(actions);

        if (mSource == Source.REMOTE) {
            mCurrentRemoteDirectory = AssetPathUtils.normalizeRemoteDirectory(initialRemoteDirectory);
            mBrowser.setRemoteDirectoryChangedListener(directory -> mCurrentRemoteDirectory = directory);
            mBrowser.navigateToRemote(mCurrentRemoteDirectory);
        } else {
            mCurrentLocalDirectory = initialLocalDirectory != null ? initialLocalDirectory : AssetBrowserPathPolicy.getLocalDraftsDir();
            if (!mCurrentLocalDirectory.exists()) {
                mCurrentLocalDirectory.mkdirs();
            }
            mBrowser.setLocalDirectoryChangedListener(directory -> mCurrentLocalDirectory = directory);
            mBrowser.navigateTo(mCurrentLocalDirectory);
        }
    }

    private void createFolderInCurrentDirectory() {
        if (mSource == Source.REMOTE) {
            mBrowser.createRemoteFolderInCurrentDirectory();
        } else {
            mBrowser.createLocalFolderInCurrentDirectory();
        }
    }

    private void chooseCurrentDirectory() {
        boolean closed = requestClose();
        if (!closed) {
            return;
        }
        if (mSource == Source.REMOTE) {
            if (mOnRemotePick != null) {
                mOnRemotePick.accept(mCurrentRemoteDirectory);
            }
        } else if (mOnLocalPick != null) {
            mOnLocalPick.accept(mCurrentLocalDirectory);
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
