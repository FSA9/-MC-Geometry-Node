package com.mine.geometry_node.core.node.nodes.data.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

public final class GetAreaRadius extends AreaPropertyNode {
    public static final String TYPE_ID = "get_area_radius";

    public GetAreaRadius() {
        super(TYPE_ID, StandardPorts.RADIUS.toOutput());
    }

    @Override
    protected @Nullable Object read(GraphDataContext context, AreaResource resource) {
        AreaResource.Resolved resolved = resolve(context, resource);
        if (resolved == null) return null;
        return switch (resolved.shape()) {
            case SPHERE -> (float) (Math.max(resolved.size().x,
                    Math.max(resolved.size().y, resolved.size().z)) * 0.5D);
            case CYLINDER -> (float) (Math.max(resolved.size().x, resolved.size().z) * 0.5D);
            case BOX -> 0.0F;
        };
    }
}
