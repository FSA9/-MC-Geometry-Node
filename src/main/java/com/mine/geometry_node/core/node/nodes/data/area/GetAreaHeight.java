package com.mine.geometry_node.core.node.nodes.data.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaShape;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

public final class GetAreaHeight extends AreaPropertyNode {
    public static final String TYPE_ID = "get_area_height";

    public GetAreaHeight() {
        super(TYPE_ID, StandardPorts.HEIGHT.toOutput());
    }

    @Override
    protected @Nullable Object read(GraphDataContext context, AreaResource resource) {
        AreaResource.Resolved resolved = resolve(context, resource);
        if (resolved == null) return null;
        return resolved.shape() == AreaShape.CYLINDER ? (float) resolved.size().y : 0.0F;
    }
}
