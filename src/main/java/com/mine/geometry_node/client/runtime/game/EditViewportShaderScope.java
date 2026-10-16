package com.mine.geometry_node.client.runtime.game;

/** Selects basic shading inside editor rendering and terrain maintenance on the rendering thread.
 * Chunk workers and Iris' settings/reload code must still see the real shader pack.
 */
public final class EditViewportShaderScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private final Boolean previous;
    private boolean closed;

    private EditViewportShaderScope(boolean basicShading) {
        previous = ACTIVE.get();
        ACTIVE.set(basicShading || Boolean.TRUE.equals(previous));
    }

    public static EditViewportShaderScope enter(boolean basicShading) {
        return new EditViewportShaderScope(basicShading);
    }

    public static boolean isActive() { return Boolean.TRUE.equals(ACTIVE.get()); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
