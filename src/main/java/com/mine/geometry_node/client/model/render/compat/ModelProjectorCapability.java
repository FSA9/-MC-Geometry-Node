package com.mine.geometry_node.client.model.render.compat;

import java.util.Set;

/** Stable result of a versioned material-projector probe. */
public enum ModelProjectorCapability {
    ACTIVE(ModelCompatibilityProfile.IRIS_1_11_LABPBR, true, Set.of()),
    INACTIVE(ModelCompatibilityProfile.ENTITY, false, Set.of()),
    RUNTIME_FAILED(ModelCompatibilityProfile.ENTITY, false,
            Set.of(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE));

    private final ModelCompatibilityProfile profile;
    private final boolean auxiliaryEnabled;
    private final Set<ModelCompatibilityLoss> frameLosses;

    ModelProjectorCapability(ModelCompatibilityProfile profile, boolean auxiliaryEnabled,
                             Set<ModelCompatibilityLoss> frameLosses) {
        this.profile = profile;
        this.auxiliaryEnabled = auxiliaryEnabled;
        this.frameLosses = frameLosses;
    }

    public ModelCompatibilityProfile profile() { return profile; }
    public boolean auxiliaryEnabled() { return auxiliaryEnabled; }
    public Set<ModelCompatibilityLoss> frameLosses() { return frameLosses; }
}
