package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.editor.datalibrary.ClientDataLibraryRepository;
import com.mine.geometry_node.client.ui.editor.datalibrary.DataLibraryEntryPresentationResolver;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.document.NodeData;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.TextView;

import java.util.UUID;

/** Compact, read-only Data Library metadata shown under a reference UUID. */
final class DataLibraryReferenceInfoView extends TextView {
    static final float HEIGHT_DP = 64.0f;
    private final NodeData nodeData;
    private final Runnable refreshTask = this::refreshPresentation;
    private final Runnable refreshListener = () -> {
        removeCallbacks(refreshTask);
        post(refreshTask);
    };

    DataLibraryReferenceInfoView(Context context, NodeData nodeData) {
        super(context);
        this.nodeData = nodeData;
        setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        UIUtils.setLockedTextSize(this, 10.0f);
        setTextColor(0xFFB9BEC6);
        setPadding(UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(2),
                UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(2));

        refreshPresentation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ClientDataLibraryRepository.INSTANCE.addChangeListener(refreshListener);
        refreshPresentation();
        ClientDataLibraryRepository.INSTANCE.ensureLoaded(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        ClientDataLibraryRepository.INSTANCE.removeChangeListener(refreshListener);
        removeCallbacks(refreshTask);
        super.onDetachedFromWindow();
    }

    private void refreshPresentation() {
        DataLibraryEntryPresentationResolver.Presentation presentation = resolve(nodeData);
        if (presentation == null) {
            setText(getUnavailableText());
            return;
        }
        setText(tr("geometry_node.port.path") + ": " + presentation.path()
                + "\n" + tr("geometry_node.port.key") + ": " + presentation.key()
                + "    " + tr("geometry_node.port.type") + ": " + tr(presentation.type().getTranslationKey())
                + "\n" + tr("geometry_node.port.data_library_value") + ": " + presentation.value());


    }

    private static DataLibraryEntryPresentationResolver.Presentation resolve(NodeData nodeData) {
        Object raw = nodeData.inputs.get("entry_id");
        if (!(raw instanceof String text) || text.isBlank()) return null;
        try {
            return DataLibraryEntryPresentationResolver.resolve(UUID.fromString(text.trim()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String getUnavailableText() {
        return tr("geometry_node.data_library.reference_unavailable");
    }

    private static String tr(String key) {
        return net.minecraft.network.chat.Component.translatable(key).getString();
    }
}
