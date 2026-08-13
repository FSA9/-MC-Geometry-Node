package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelImageSource;

import java.io.IOException;

@FunctionalInterface
public interface ModelImageDecoder {
    DecodedModelImage decode(ModelImageSource source) throws IOException;
}
