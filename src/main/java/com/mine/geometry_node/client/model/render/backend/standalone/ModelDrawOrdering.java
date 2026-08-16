package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelVector3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Pure camera-space ordering helpers without a Minecraft renderer dependency. */
public final class ModelDrawOrdering {
    private ModelDrawOrdering() {}

    public static float viewDepth(ModelBounds bounds, Matrix4fc modelView) {
        ModelVector3 min = bounds.min(), max = bounds.max();
        return modelView.transformPosition(new Vector3f((float) ((min.x() + max.x()) * 0.5),
                (float) ((min.y() + max.y()) * 0.5), (float) ((min.z() + max.z()) * 0.5))).z;
    }

    public static int compareTransparentDepth(float left, float right) {
        if (!Float.isFinite(left)) return Float.isFinite(right) ? 1 : 0;
        if (!Float.isFinite(right)) return -1;
        return Float.compare(left, right);
    }
}
