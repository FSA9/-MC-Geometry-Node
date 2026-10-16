package com.mine.geometry_node.client.runtime.game;

/** Selects basic shading only inside an orthographic frame on the rendering thread.
 * Chunk workers and Iris' settings/reload code must still see the real shader pack.
 */
public final class EditViewportShaderScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private final Boolean previous;
    private boolean closed;

    private EditViewportShaderScope(boolean orthographic) {
        previous = ACTIVE.get();
        ACTIVE.set(orthographic || Boolean.TRUE.equals(previous));
    }

    public static EditViewportShaderScope enter(boolean orthographic) {
        return new EditViewportShaderScope(orthographic);
    }

    public static boolean isActive() { return Boolean.TRUE.equals(ACTIVE.get()); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }
}
