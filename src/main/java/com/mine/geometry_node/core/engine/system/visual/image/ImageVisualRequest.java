package com.mine.geometry_node.core.engine.system.visual.image;

import com.mine.geometry_node.core.engine.blueprint.runtime.wait.BlueprintExternalWaitRequest;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Immutable visual parameters captured before a Blueprint waits for server image I/O. */
public record ImageVisualRequest(
        String relativePath,
        int durationTicks,
        Map<String, ExpressionData> expressions,
        CompoundTag extraData,
        Vec3 center,
        double radius
) implements BlueprintExternalWaitRequest {
    public static final String NEXT_PORT = StandardPorts.FLOW_OUT.getId();

    public ImageVisualRequest {
        relativePath = relativePath == null ? "" : relativePath;
        expressions = expressions == null ? Map.of() : Map.copyOf(expressions);
        extraData = extraData == null ? new CompoundTag() : extraData.copy();
        center = center == null ? Vec3.ZERO : center;
    }
}
