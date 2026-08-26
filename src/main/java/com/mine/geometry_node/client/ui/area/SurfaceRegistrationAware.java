package com.mine.geometry_node.client.ui.area;

import com.mine.geometry_node.client.ui.surface.UiSurfaceRegistry;

/** Implemented by editor windows that publish type-specific context through their registration. */
public interface SurfaceRegistrationAware {
    void bindSurfaceRegistration(UiSurfaceRegistry.Registration registration);
}
