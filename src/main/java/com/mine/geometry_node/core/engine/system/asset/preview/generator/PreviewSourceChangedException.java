package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import java.io.IOException;

public final class PreviewSourceChangedException extends IOException {
    public PreviewSourceChangedException() {
        super("Asset source revision changed while generating its preview");
    }
}
