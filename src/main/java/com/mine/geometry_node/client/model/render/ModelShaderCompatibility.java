package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.compat.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.render.compat.ModelCompatibilityProfile;
import com.mine.geometry_node.client.model.render.compat.ModelDrawRejection;
import com.mine.geometry_node.client.model.render.compat.ModelShaderBackendStatus;
import com.mine.geometry_node.client.model.render.compat.ModelIntegrationCapability;
import com.mine.geometry_node.client.model.render.compat.ModelIntegrationFallback;
import com.mine.geometry_node.client.model.render.compat.ModelIntegrationMode;
import com.mine.geometry_node.client.model.render.compat.ModelIntegrationStatus;
import com.mine.geometry_node.client.model.render.compat.ModelIntegrationVerification;
import com.mojang.blaze3d.systems.RenderPass;

import java.util.EnumSet;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves the requested integration policy to an effective rendering profile. */
public final class ModelShaderCompatibility {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final Set<ModelIntegrationCapability> HOST_NATIVE_CAPABILITIES = Set.copyOf(EnumSet.of(
            ModelIntegrationCapability.HOST_ENTITY_SHADER,
            ModelIntegrationCapability.BASE_COLOR,
            ModelIntegrationCapability.ALPHA_MODES));
    private static volatile ModelIntegrationMode requestedMode = ModelIntegrationMode.HOST_NATIVE;
    private static volatile boolean hostNativeRequired;
    private static volatile HostEnvironment hostEnvironment = HostEnvironment.ABSENT;
    private static IrisApiBinding irisApiBinding;
    private static boolean irisApiResolved;
    private static String irisApiFailure = "";
    private static long generation;
    private static volatile ModelIntegrationStatus integrationStatus = standaloneStatus();
    private static volatile ModelShaderBackendStatus status = legacyStatus(integrationStatus);
    private static String loggedDiagnostic = "";

    private ModelShaderCompatibility() { }

    public static void decorate(RenderPass pass) {
        // HOST_NATIVE is submitted through the host entity path and owns no custom RenderPass state.
    }

    public static ModelShaderBackendStatus status() { return status; }

    public static ModelIntegrationStatus integrationStatus() { return integrationStatus; }

    public static ModelIntegrationMode requestedMode() { return requestedMode; }

    public static void requestMode(ModelIntegrationMode mode) {
        ModelIntegrationMode next = mode == null ? ModelIntegrationMode.HOST_NATIVE : mode;
        if (requestedMode == next) return;
        requestedMode = next;
        generation++;
        publish(hostNativeRequired
                ? hostNativeStatus(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
                        Set.of(), ModelIntegrationVerification.PENDING, List.of(), Map.of())
                : standaloneStatus());
    }

    /** Called once from the client tick; render callbacks only consume this immutable decision. */
    public static void captureFrameEnvironment() {
        HostEnvironment observed = observeHostEnvironment();
        boolean next = observed == HostEnvironment.ACTIVE || observed == HostEnvironment.FAILED;
        if (next == hostNativeRequired && observed == hostEnvironment) return;
        generation++;
        hostEnvironment = observed;
        hostNativeRequired = next;
        publish(next ? hostNativeStatus(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
                Set.of(), ModelIntegrationVerification.PENDING,
                observed == HostEnvironment.FAILED ? List.of(irisApiFailure) : List.of(), Map.of())
                : standaloneStatus());
    }

    public static boolean requiresCompatibilityBackend() {
        return hostNativeRequired;
    }

    public static void reportCompatibility(Set<ModelCompatibilityLoss> losses) {
        reportCompatibility(losses, ModelIntegrationVerification.UNVERIFIED, List.of(), Map.of());
    }

    public static void reportCompatibility(Set<ModelCompatibilityLoss> semanticLosses,
                                           ModelIntegrationVerification verification,
                                           List<String> runtimeFaults,
                                           Map<ModelDrawRejection, Integer> rejectedDraws) {
        reportCompatibility(ModelCompatibilityProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
                semanticLosses, verification, runtimeFaults, rejectedDraws);
    }

    public static void reportCompatibility(ModelCompatibilityProfile profile,
                                           Set<ModelIntegrationCapability> capabilities,
                                           Set<ModelCompatibilityLoss> semanticLosses,
                                           ModelIntegrationVerification verification,
                                           List<String> runtimeFaults,
                                           Map<ModelDrawRejection, Integer> rejectedDraws) {
        List<String> combinedFaults;
        if (hostEnvironment == HostEnvironment.FAILED && !irisApiFailure.isEmpty()) {
            java.util.LinkedHashSet<String> faults = new java.util.LinkedHashSet<>(runtimeFaults);
            faults.add(irisApiFailure);
            combinedFaults = List.copyOf(faults);
        } else {
            combinedFaults = runtimeFaults;
        }
        publish(hostNativeStatus(profile, capabilities, semanticLosses, verification, combinedFaults, rejectedDraws));
    }

    public static void reset() {
        requestedMode = ModelIntegrationMode.HOST_NATIVE;
        hostNativeRequired = false;
        hostEnvironment = HostEnvironment.ABSENT;
        generation++;
        integrationStatus = standaloneStatus();
        status = legacyStatus(integrationStatus);
        loggedDiagnostic = "";
    }

    private static ModelIntegrationStatus standaloneStatus() {
        return new ModelIntegrationStatus(requestedMode, ModelIntegrationMode.HOST_NATIVE,
                ModelIntegrationStatus.Fidelity.FULL, "standalone", false, HOST_NATIVE_CAPABILITIES,
                Set.of(), ModelIntegrationVerification.NOT_REQUIRED, List.of(), Map.of(), generation,
                requestedMode == ModelIntegrationMode.HOST_NATIVE ? ModelIntegrationFallback.NONE
                        : ModelIntegrationFallback.REQUESTED_MODE_UNAVAILABLE,
                requestedMode == ModelIntegrationMode.HOST_NATIVE ? "" : "MODEL_TAKEOVER is not implemented");
    }

    private static ModelIntegrationStatus hostNativeStatus(ModelCompatibilityProfile profile,
                                                            Set<ModelIntegrationCapability> capabilities,
                                                            Set<ModelCompatibilityLoss> semanticLosses,
                                                            ModelIntegrationVerification verification,
                                                            List<String> runtimeFaults,
                                                            Map<ModelDrawRejection, Integer> rejectedDraws) {
        Set<ModelIntegrationCapability> reportedCapabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        ModelIntegrationStatus.Fidelity fidelity = semanticLosses.isEmpty() && runtimeFaults.isEmpty()
                && rejectedDraws.isEmpty()
                    && verification == ModelIntegrationVerification.VERIFIED
                ? ModelIntegrationStatus.Fidelity.FULL : ModelIntegrationStatus.Fidelity.DEGRADED;
        return new ModelIntegrationStatus(requestedMode, ModelIntegrationMode.HOST_NATIVE,
                fidelity, profile == ModelCompatibilityProfile.HOST_NATIVE_LABPBR
                        ? "host-native-labpbr-1.3" : "host-native-entity", true, reportedCapabilities,
                semanticLosses, verification, runtimeFaults,
                rejectedDraws, generation,
                requestedMode == ModelIntegrationMode.HOST_NATIVE ? ModelIntegrationFallback.NONE
                        : ModelIntegrationFallback.REQUESTED_MODE_UNAVAILABLE,
                requestedMode == ModelIntegrationMode.HOST_NATIVE ? "" : "MODEL_TAKEOVER is not implemented");
    }

    private static HostEnvironment observeHostEnvironment() {
        resolveIrisApi();
        if (irisApiBinding == null) {
            return irisApiFailure.isEmpty() ? HostEnvironment.ABSENT : HostEnvironment.FAILED;
        }
        try {
            Object api = irisApiBinding.getInstance().invoke(null);
            return (boolean) irisApiBinding.isShaderPackInUse().invoke(api)
                    ? HostEnvironment.ACTIVE : HostEnvironment.INACTIVE;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            irisApiFailure = "Iris public API runtime failure: " + exception.getClass().getSimpleName();
            return HostEnvironment.FAILED;
        }
    }

    private static synchronized void resolveIrisApi() {
        if (irisApiResolved) return;
        irisApiResolved = true;
        try {
            Class<?> apiClass = Class.forName(IRIS_API, false, ModelShaderCompatibility.class.getClassLoader());
            irisApiBinding = new IrisApiBinding(apiClass.getMethod("getInstance"),
                    apiClass.getMethod("isShaderPackInUse"));
        } catch (ClassNotFoundException absent) {
            irisApiFailure = "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            irisApiFailure = "Iris public API contract failure: " + failure.getClass().getSimpleName();
        }
    }

    private enum HostEnvironment { ABSENT, INACTIVE, ACTIVE, FAILED }

    private record IrisApiBinding(Method getInstance, Method isShaderPackInUse) { }

    private static ModelShaderBackendStatus legacyStatus(ModelIntegrationStatus next) {
        String diagnostic = "Model integration requested=" + next.requestedMode() + " effective="
                + next.effectiveMode() + " profile=" + next.profileId() + " fidelity=" + next.fidelity()
                + " losses=" + next.semanticLosses() + " verification=" + next.verification()
                + " runtimeFaults=" + next.runtimeFaults() + " rejectedDraws=" + next.rejectedDraws()
                + " generation=" + next.generation()
                + (next.fallback() == ModelIntegrationFallback.NONE ? "" : " fallback=" + next.fallback());
        return next.fidelity() == ModelIntegrationStatus.Fidelity.FULL
                ? ModelShaderBackendStatus.full(next.profileId(), next.shaderEnvironmentPresent(), diagnostic)
                : ModelShaderBackendStatus.compatibility(next.profileId(),
                        next.semanticLosses().stream().map(Enum::name).sorted().toList(), diagnostic);
    }

    private static synchronized void publish(ModelIntegrationStatus next) {
        integrationStatus = next;
        status = legacyStatus(next);
        if (status.diagnostic().equals(loggedDiagnostic)) return;
        loggedDiagnostic = status.diagnostic();
        if (next.fidelity() == ModelIntegrationStatus.Fidelity.DEGRADED) GeometryNode.LOGGER.warn("{}", status.diagnostic());
        else GeometryNode.LOGGER.info("{}", status.diagnostic());
    }
}
