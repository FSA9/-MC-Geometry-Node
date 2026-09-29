package com.mine.geometry_node.core.engine.system.model.asset;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
/** Encoded dependency closure is distinct from decoded CPU/GPU residency. */
public record ModelLimits(long packageBytes, int jsonBytes, int readSliceBytes, long decodedAccessorBytes, int dependencies) {
    public static final long MAX_PACKAGE_BYTES = 512L * 1024 * 1024;
    public static final ModelLimits DEFAULT = new ModelLimits(MAX_PACKAGE_BYTES, 16 * 1024 * 1024,
            16 * 1024 * 1024, 256L * 1024 * 1024, 4096);
    public ModelLimits {
        if (packageBytes < 1 || packageBytes > MAX_PACKAGE_BYTES || jsonBytes < 1 || readSliceBytes < jsonBytes
                || decodedAccessorBytes < 1 || dependencies < 1) throw new IllegalArgumentException("invalid model limits");
    }
    public static void requireWithin(long value, long maximum, String location) throws ModelProblem {
        if (value < 0 || value > maximum) throw new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED,
                location, value + " bytes/items exceeds " + maximum);
    }
}
