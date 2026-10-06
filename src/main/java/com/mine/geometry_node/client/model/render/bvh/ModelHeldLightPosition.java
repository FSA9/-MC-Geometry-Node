package com.mine.geometry_node.client.model.render.bvh;

import org.joml.Vector3d;

/** A stable hand-space light origin, independent of the first/third-person camera. */
final class ModelHeldLightPosition {
    static Vector3d offset(float yawDegrees, float pitchDegrees, boolean rightHand) {
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
        double side = rightHand ? 0.3 : -0.3;
        double down = 0.25, forward = 0.3;
        return new Vector3d(
                -cosYaw * side + sinYaw * sinPitch * down - sinYaw * cosPitch * forward,
                -cosPitch * down - sinPitch * forward,
                -sinYaw * side - cosYaw * sinPitch * down + cosYaw * cosPitch * forward);
    }

    private ModelHeldLightPosition() {}
}
