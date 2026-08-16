package com.mine.geometry_node.client.model.render.backend.host.geometry;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostGeometryProjectorTest {
    @Test
    void lightSamplingRepresentativePointUsesPrimitiveBoundsCenter() {
        var center = HostGeometryProjector.boundsCenter(new ModelBounds(
                new ModelVector3(-4, 2, 8), new ModelVector3(6, 10, 12)));
        assertEquals(1, center.x());
        assertEquals(6, center.y());
        assertEquals(10, center.z());
    }
}
