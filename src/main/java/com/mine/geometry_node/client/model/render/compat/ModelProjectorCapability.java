package com.mine.geometry_node.client.model.render.compat;

/** Stable result of a versioned material-projector probe. */
public enum ModelProjectorCapability {
    ACTIVE(ModelCompatibilityProfile.HOST_NATIVE_LABPBR, true, ModelIntegrationVerification.UNVERIFIED, false),
    PENDING(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.PENDING, false),
    UNVERIFIED(ModelCompatibilityProfile.HOST_NATIVE_LABPBR, true, ModelIntegrationVerification.UNVERIFIED, false),
    ABSENT(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.NOT_APPLICABLE, false),
    INACTIVE(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.NOT_APPLICABLE, false),
    FAILED(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.UNVERIFIED, true);

    private final ModelCompatibilityProfile profile;
    private final boolean auxiliaryEnabled;
    private final ModelIntegrationVerification verification;
    private final boolean runtimeFault;

    ModelProjectorCapability(ModelCompatibilityProfile profile, boolean auxiliaryEnabled,
                             ModelIntegrationVerification verification, boolean runtimeFault) {
        this.profile = profile;
        this.auxiliaryEnabled = auxiliaryEnabled;
        this.verification = verification;
        this.runtimeFault = runtimeFault;
    }

    public ModelCompatibilityProfile profile() { return profile; }
    public boolean auxiliaryEnabled() { return auxiliaryEnabled; }
    public ModelIntegrationVerification verification() { return verification; }
    public boolean runtimeFault() { return runtimeFault; }
}
