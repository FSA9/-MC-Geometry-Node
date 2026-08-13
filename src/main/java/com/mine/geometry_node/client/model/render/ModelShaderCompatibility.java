package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.compat.ModelShaderBackend;
import com.mine.geometry_node.client.model.render.compat.ModelShaderBackendStatus;
import com.mine.geometry_node.client.model.render.compat.iris.Iris111ModelShaderAdapter;
import com.mojang.blaze3d.systems.RenderPass;

import java.util.List;
import java.util.Set;
import com.mine.geometry_node.client.model.render.compat.ModelCompatibilityLoss;

/** Selects an isolated shader-environment adapter without exposing it to the model domain. */
public final class ModelShaderCompatibility {
    private static final ModelShaderBackend STANDALONE = ModelShaderBackend.standalone();
    private static final List<ModelShaderBackend> ADAPTERS = List.of(new Iris111ModelShaderAdapter());
    private static volatile ModelShaderBackendStatus status = STANDALONE.probe();
    private static String loggedDiagnostic = "";

    private ModelShaderCompatibility() {
    }

    public static void decorate(RenderPass pass) {
        ModelShaderBackend selected = select();
        try {
            selected.decorate(pass);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ModelShaderBackendStatus failed = selected.failed(exception);
            publish(failed);
            throw new IllegalStateException(failed.diagnostic(), exception);
        }
    }

    public static ModelShaderBackendStatus status() {
        return status;
    }

    public static boolean requiresCompatibilityBackend() {
        for (ModelShaderBackend adapter : ADAPTERS) {
            ModelShaderBackendStatus candidate = adapter.probe();
            if (!candidate.shaderEnvironmentPresent()) continue;
            if (!candidate.selectable() && !status.id().equals("entity-compatibility")) publish(candidate);
            return !candidate.selectable();
        }
        return false;
    }

    public static void reportCompatibility(Set<ModelCompatibilityLoss> losses) {
        List<String> codes = losses.stream().map(Enum::name).sorted().toList();
        publish(ModelShaderBackendStatus.compatibility("entity-compatibility", codes,
                "Model shader backend=entity-compatibility fidelity=COMPATIBILITY losses=" + codes));
    }

    public static void reset() {
        status = STANDALONE.probe();
        loggedDiagnostic = "";
    }

    private static ModelShaderBackend select() {
        for (ModelShaderBackend adapter : ADAPTERS) {
            ModelShaderBackendStatus candidate = adapter.probe();
            if (candidate.shaderEnvironmentPresent()) {
                if (!candidate.selectable()) {
                    if (!status.id().equals("entity-compatibility")) publish(candidate);
                    return STANDALONE;
                }
                publish(candidate);
                return adapter;
            }
        }
        ModelShaderBackendStatus standalone = STANDALONE.probe();
        publish(standalone);
        return STANDALONE;
    }

    private static synchronized void publish(ModelShaderBackendStatus next) {
        status = next;
        if (next.diagnostic().equals(loggedDiagnostic)) return;
        loggedDiagnostic = next.diagnostic();
        if (next.degraded() || !next.selectable()) GeometryNode.LOGGER.warn("{}", next.diagnostic());
        else GeometryNode.LOGGER.info("{}", next.diagnostic());
    }
}
