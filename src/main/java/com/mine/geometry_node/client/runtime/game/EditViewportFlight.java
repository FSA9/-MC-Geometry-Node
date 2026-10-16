package com.mine.geometry_node.client.runtime.game;

import java.util.EnumSet;

/** Flight navigation math/state; no Minecraft, window or UI dependencies. */
public final class EditViewportFlight {
    public enum Direction { FORWARD, BACKWARD, LEFT, RIGHT, UP, DOWN }
    public static final double SPEED = 8.0; // blocks per second
    private final EditViewportCamera camera;
    private final EnumSet<Direction> held = EnumSet.noneOf(Direction.class);
    private boolean active;

    public EditViewportFlight(EditViewportCamera camera) { this.camera = camera; }

    public boolean start() {
        if (active || !camera.isInitialized()) return false;
        held.clear();
        active = true;
        return true;
    }

    public void stop() {
        held.clear();
        active = false;
    }

    public void setHeld(Direction direction, boolean down) {
        if (!active) return;
        if (down) held.add(direction);
        else held.remove(direction);
    }

    public void look(double yaw, double pitch) { if (active) camera.look(yaw, pitch); }

    public void advance(double seconds) {
        if (!active || !Double.isFinite(seconds) || seconds <= 0) return;
        var pose = camera.snapshot();
        double yaw = Math.toRadians(pose.yaw()), pitch = Math.toRadians(pose.pitch());
        double forward = axis(Direction.FORWARD, Direction.BACKWARD);
        double right = axis(Direction.RIGHT, Direction.LEFT);
        double x = -Math.sin(yaw) * Math.cos(pitch) * forward - Math.cos(yaw) * right;
        double y = -Math.sin(pitch) * forward + axis(Direction.UP, Direction.DOWN);
        double z = Math.cos(yaw) * Math.cos(pitch) * forward - Math.sin(yaw) * right;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-8) return;
        // Constant speed for diagonals as well; cap stalls to prevent large jumps.
        double scale = SPEED * Math.min(seconds, 0.1) / length;
        camera.translate(x * scale, y * scale, z * scale);
    }

    private int axis(Direction positive, Direction negative) {
        return (held.contains(positive) ? 1 : 0) - (held.contains(negative) ? 1 : 0);
    }
}
