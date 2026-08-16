package com.mine.geometry_node.client.model.render.backend.host.iris.labpbr;

import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialProfile;
import com.mine.geometry_node.client.model.render.integration.ModelIntegrationVerification;

/** Stable result of a versioned material-projector probe. */
public enum ModelProjectorCapability {
    ACTIVE(HostMaterialProfile.HOST_NATIVE_LABPBR, true, ModelIntegrationVerification.UNVERIFIED, false),
    PENDING(HostMaterialProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.PENDING, false),
    UNVERIFIED(HostMaterialProfile.HOST_NATIVE_LABPBR, true, ModelIntegrationVerification.UNVERIFIED, false),
    ABSENT(HostMaterialProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.NOT_APPLICABLE, false),
    INACTIVE(HostMaterialProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.NOT_APPLICABLE, false),
    FAILED(HostMaterialProfile.HOST_NATIVE_ENTITY, false, ModelIntegrationVerification.UNVERIFIED, true);

    private final HostMaterialProfile profile;
    private final boolean auxiliaryEnabled;
    private final ModelIntegrationVerification verification;
    private final boolean runtimeFault;

    ModelProjectorCapability(HostMaterialProfile profile, boolean auxiliaryEnabled,
                             ModelIntegrationVerification verification, boolean runtimeFault) {
        this.profile = profile;
        this.auxiliaryEnabled = auxiliaryEnabled;
        this.verification = verification;
        this.runtimeFault = runtimeFault;
    }

    public HostMaterialProfile profile() { return profile; }
    public boolean auxiliaryEnabled() { return auxiliaryEnabled; }
    public ModelIntegrationVerification verification() { return verification; }
    public boolean runtimeFault() { return runtimeFault; }
}
