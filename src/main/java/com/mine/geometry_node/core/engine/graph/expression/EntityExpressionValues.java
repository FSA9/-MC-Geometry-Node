package com.mine.geometry_node.core.engine.graph.expression;

import com.mine.geometry_node.core.engine.system.display.DisplayTransformController;
import net.minecraft.world.entity.Entity;

/** Shared property semantics for typed entity expression bindings. */
public final class EntityExpressionValues {
    private EntityExpressionValues() {
    }

    public static double resolve(ExpressionBinding.EntityProperty binding, Entity entity, float partialTick) {
        if (binding == null || entity == null || !entity.getUUID().equals(binding.entityUuid())) {
            return Double.NaN;
        }
        return switch (binding.property()) {
            case VELOCITY -> entity.getDeltaMovement().length();
            case VELOCITY_X -> entity.getDeltaMovement().x;
            case VELOCITY_Y -> entity.getDeltaMovement().y;
            case VELOCITY_Z -> entity.getDeltaMovement().z;
            case POS_X -> entity.getPosition(partialTick).x;
            case POS_Y -> entity.getPosition(partialTick).y;
            case POS_Z -> entity.getPosition(partialTick).z;
            case ROTATION_X, PITCH -> entity.getXRot();
            case ROTATION_Y, YAW -> entity.getYRot();
            case ROTATION_Z -> DisplayTransformController.worldRotation(entity).z;
            case YAW_HEAD -> entity.getYHeadRot();
        };
    }
}
