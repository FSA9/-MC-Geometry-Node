package com.mine.geometry_node.core.engine.system.model.gltf;

import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;

/** Aggregate retained CPU budget, checked before allocating each immutable payload. */
final class GltfImportBudget {
    private final long maximum; private long retained;
    GltfImportBudget(ModelLimits limits) { maximum = limits.decodedAccessorBytes(); }
    void reserve(long bytes, String path) throws ModelProblem {
        if (bytes < 0 || bytes > maximum - retained) throw new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, path,
                "aggregate decoded payload exceeds " + maximum + " bytes");
        retained += bytes;
    }
    void checkTemporary(long bytes, String path) throws ModelProblem {
        if (bytes < 0 || bytes > maximum - retained) throw new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, path,
                "tangent working set exceeds aggregate decoded budget " + maximum + " bytes");
    }
    long retained() { return retained; }
}
