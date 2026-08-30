package com.mine.geometry_node.client.ui.components.nativepreview;

/**
 * Host for native previews that share a single raw-render surface.
 */
public interface NativePreviewHost {
    Registration registerNativePreview(ViewportNativePreview preview);

    interface Registration extends AutoCloseable {
        void requestRender();

        void notifyOrderChanged();

        @Override
        void close();
    }
}
