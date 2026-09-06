package com.mine.geometry_node.core.node.nodes.data.area;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;

public final class GetAreaShape extends AreaPropertyNode {
    public static final String TYPE_ID = "get_area_shape";

    public GetAreaShape() {
        super(TYPE_ID, StandardPorts.SHAPE.toOutput());
    }

    @Override
    protected Object read(GraphDataContext context, AreaResource resource) {
        return resource.shape().id();
    }
}
