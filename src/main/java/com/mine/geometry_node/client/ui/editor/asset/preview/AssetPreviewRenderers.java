package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.schematic.SchematicThumbnailView;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Runtime registry for preview renderers. Extensions can add video/model renderers without editing the view. */
public final class AssetPreviewRenderers {
    private static final CopyOnWriteArrayList<AssetPreviewRenderer> RENDERERS = new CopyOnWriteArrayList<>();

    static {
        register(new AssetPreviewRenderer() {
            @Override public boolean supports(AssetEntry entry, AssetPreviewArtifact artifact) {
                return entry != null && artifact != null && entry.sourceKind() == AssetSourceKind.LOCAL
                        && entry.type().previewKind().equals(AssetPreviewKind.SCHEMATIC);
            }
            @Override public View create(Context context, AssetEntry entry, AssetPreviewArtifact artifact) {
                return new SchematicThumbnailView(context, artifact.file());
            }
        });
        register(new AssetPreviewRenderer() {
            @Override public boolean supports(AssetEntry entry, AssetPreviewArtifact artifact) {
                if (artifact == null || !artifact.file().isFile()
                        || !artifact.kind().equals(AssetPreviewKind.VIDEO)) return false;
                String mediaType = artifact.mediaType();
                return mediaType.equals("png") || mediaType.equals("jpg")
                        || mediaType.equals("jpeg") || mediaType.equals("image/png")
                        || mediaType.equals("image/jpeg");
            }
            @Override public View create(Context context, AssetEntry entry, AssetPreviewArtifact artifact) {
                return new ImageThumbnailView(context, artifact.file());
            }
        });
        register(new AssetPreviewRenderer() {
            @Override public boolean supports(AssetEntry entry, AssetPreviewArtifact artifact) {
                return artifact != null && artifact.file().isFile()
                        && (artifact.kind().equals(AssetPreviewKind.IMAGE)
                        || entry != null && entry.type().previewKind().equals(AssetPreviewKind.IMAGE));
            }
            @Override public View create(Context context, AssetEntry entry, AssetPreviewArtifact artifact) {
                return new ImageThumbnailView(context, artifact.file());
            }
        });
    }

    private AssetPreviewRenderers() {}

    public static void register(AssetPreviewRenderer renderer) { RENDERERS.addIfAbsent(renderer); }
    public static void unregister(AssetPreviewRenderer renderer) { RENDERERS.remove(renderer); }
    public static List<AssetPreviewRenderer> all() { return List.copyOf(RENDERERS); }

    public static View create(Context context, AssetEntry entry, AssetPreviewArtifact artifact) {
        for (AssetPreviewRenderer renderer : RENDERERS) {
            if (renderer.supports(entry, artifact)) return renderer.create(context, entry, artifact);
        }
        return null;
    }
}
