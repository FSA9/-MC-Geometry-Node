package com.mine.geometry_node.client.model.render.integration;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.backend.host.material.HostMaterialProfile;

import java.util.EnumSet;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves the requested integration policy to an effective rendering profile. */
public final class ModelIntegrationController {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final Set<ModelIntegrationCapability> HOST_NATIVE_CAPABILITIES = Set.copyOf(EnumSet.of(
            ModelIntegrationCapability.HOST_ENTITY_SHADER,
            ModelIntegrationCapability.BASE_COLOR,
            ModelIntegrationCapability.ALPHA_MODES));
    private static volatile ModelIntegrationMode requestedMode = ModelIntegrationMode.NATIVE;
    private static volatile boolean hostNativeRequired;
    private static volatile HostEnvironment hostEnvironment = HostEnvironment.ABSENT;
    private static IrisApiBinding irisApiBinding;
    private static boolean irisApiResolved;
    private static String irisApiFailure = "";
    private static long generation;
    private static volatile ModelIntegrationStatus integrationStatus = standaloneStatus();
    private static String loggedDiagnostic = "";

    private ModelIntegrationController() { }

    public static ModelIntegrationStatus integrationStatus() { return integrationStatus; }

    public static ModelIntegrationMode requestedMode() { return requestedMode; }

    public static void requestMode(ModelIntegrationMode mode) {
        ModelIntegrationMode next = mode == null ? ModelIntegrationMode.NATIVE : mode;
        if (requestedMode == next) return;
        requestedMode = next;
        generation++;
        publish(hostNativeRequired
                ? hostNativeStatus(HostMaterialProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
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
        publish(next ? hostNativeStatus(HostMaterialProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
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
        reportCompatibility(HostMaterialProfile.HOST_NATIVE_ENTITY, HOST_NATIVE_CAPABILITIES,
                semanticLosses, verification, runtimeFaults, rejectedDraws);
    }

    public static void reportCompatibility(HostMaterialProfile profile,
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
        requestedMode = ModelIntegrationMode.NATIVE;
        hostNativeRequired = false;
        hostEnvironment = HostEnvironment.ABSENT;
        generation++;
        integrationStatus = standaloneStatus();
        loggedDiagnostic = "";
    }

    private static ModelIntegrationStatus standaloneStatus() {
        return new ModelIntegrationStatus(requestedMode, ModelIntegrationMode.NATIVE,
                ModelNativeProfile.STANDALONE, ModelIntegrationStatus.Fidelity.FULL, "standalone", false,
                Set.of(ModelIntegrationCapability.BASE_COLOR, ModelIntegrationCapability.ALPHA_MODES),
                Set.of(), ModelIntegrationVerification.NOT_REQUIRED, List.of(), Map.of(), generation,
                requestedMode == ModelIntegrationMode.NATIVE ? ModelIntegrationFallback.NONE
                        : ModelIntegrationFallback.REQUESTED_MODE_UNAVAILABLE,
                requestedMode == ModelIntegrationMode.NATIVE ? "" : "TAKEOVER is not implemented");
    }

    private static ModelIntegrationStatus hostNativeStatus(HostMaterialProfile profile,
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
        return new ModelIntegrationStatus(requestedMode, ModelIntegrationMode.NATIVE,
                ModelNativeProfile.HOST_NATIVE, fidelity, profile == HostMaterialProfile.HOST_NATIVE_LABPBR
                        ? "host-native-labpbr-1.3" : "host-native-entity", true, reportedCapabilities,
                semanticLosses, verification, runtimeFaults,
                rejectedDraws, generation,
                requestedMode == ModelIntegrationMode.NATIVE ? ModelIntegrationFallback.NONE
                        : ModelIntegrationFallback.REQUESTED_MODE_UNAVAILABLE,
                requestedMode == ModelIntegrationMode.NATIVE ? "" : "TAKEOVER is not implemented");
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
            Class<?> apiClass = Class.forName(IRIS_API, false, ModelIntegrationController.class.getClassLoader());
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

    private static synchronized void publish(ModelIntegrationStatus next) {
        integrationStatus = next;
        String diagnostic = "Model integration requested=" + next.requestedMode() + " effective="
                + next.effectiveMode() + " nativeProfile=" + next.nativeProfile() + " profile=" + next.profileId()
                + " fidelity=" + next.fidelity() + " losses=" + next.semanticLosses()
                + " verification=" + next.verification() + " runtimeFaults=" + next.runtimeFaults()
                + " rejectedDraws=" + next.rejectedDraws() + " generation=" + next.generation()
                + (next.fallback() == ModelIntegrationFallback.NONE ? "" : " fallback=" + next.fallback());
        if (diagnostic.equals(loggedDiagnostic)) return;
        loggedDiagnostic = diagnostic;
        if (!next.runtimeFaults().isEmpty() || !next.rejectedDraws().isEmpty()) {
            GeometryNode.LOGGER.warn("{}", diagnostic);
        }
    }
}
