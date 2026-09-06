package com.mine.geometry_node.core.node.nodes.data.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

public final class GetAreaRotation extends AreaPropertyNode {
    public static final String TYPE_ID = "get_area_rotation";

    public GetAreaRotation() {
        super(TYPE_ID, StandardPorts.ROTATION.toOutput());
    }

    @Override
    protected @Nullable Object read(GraphDataContext context, AreaResource resource) {
        AreaResource.Resolved resolved = resolve(context, resource);
        return resolved != null ? resolved.rotation() : null;
    }
}
