package com.mine.geometry_node.core.engine.graph.data;

import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.node.definition.port.PortConversionRegistry;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Graph-family-neutral evaluator for compiled data connections.
 * Runtime adapters provide node context and graph-family-specific execution policy.
 */
public final class CompiledGraphDataEvaluator {
    private final CompiledDataIndex index;
    private final GraphDataEvaluationSession session;

    public CompiledGraphDataEvaluator(CompiledDataIndex index) {
        this.index = Objects.requireNonNull(index, "index");
        this.session = new GraphDataEvaluationSession(index);
    }

    public void beginEpoch() {
        session.beginEpoch();
    }

    public void clearValues() {
        session.clearValues();
    }

    @Nullable
    public Object evaluateOutput(int nodeId, int portKey, RuntimeAdapter runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return session.evaluate(nodeId, portKey,
                (sourceNodeId, sourcePortKey) -> computeOutput(
                        sourceNodeId, sourcePortKey, runtime));
    }

    @Nullable
    public Object resolveInput(int targetNodeId, String portName, RuntimeAdapter runtime) {
        int portKey = index.getPortKey(portName);
        if (portKey < 0) return null;
        CompiledDataIndex.DataConnectionSource source =
                index.findDataInput(targetNodeId, portKey);
        return source != null
                ? resolveConnection(targetNodeId, source, runtime)
                : index.getStaticInput(targetNodeId, portKey);
    }

    @Nullable
    public Object connectedInput(int targetNodeId, String portName, RuntimeAdapter runtime) {
        int portKey = index.getPortKey(portName);
        if (portKey < 0) return null;
        CompiledDataIndex.DataConnectionSource source =
                index.findDataInput(targetNodeId, portKey);
        return source != null ? resolveConnection(targetNodeId, source, runtime) : null;
    }

    public boolean hasInputConnection(int targetNodeId, String portName) {
        return index.findDataInput(targetNodeId, portName) != null;
    }

    @Nullable
    public <T> T resolveInput(int targetNodeId, String portName, Class<T> type,
                              RuntimeAdapter runtime) {
        return TypeConverter.convert(resolveInput(targetNodeId, portName, runtime), type,
                runtime.contextFor(targetNodeId));
    }

    @Nullable
    public <T> T resolveInputFromList(int targetNodeId, String portName, int elementIndex,
                                      Class<T> type, RuntimeAdapter runtime) {
        return TypeConverter.convertFromList(
                resolveInput(targetNodeId, portName, runtime), elementIndex, type,
                runtime.contextFor(targetNodeId));
    }

    @Nullable
    public <T> T convertValue(int targetNodeId, Object value, Class<T> type,
                              RuntimeAdapter runtime) {
        return TypeConverter.convert(value, type, runtime.contextFor(targetNodeId));
    }

    @Nullable
    private Object computeOutput(int nodeId, int portKey, RuntimeAdapter runtime) {
        String portName = index.getPortName(portKey);
        if (portName == null) return null;
        if (index.isDataPassthroughOutput(nodeId, portKey)) {
            CompiledDataIndex.DataConnectionSource source = index.findDataInput(nodeId, portKey);
            return source != null
                    ? resolveConnection(nodeId, source, runtime)
                    : index.getStaticInput(nodeId, portKey);
        }
        return runtime.computeNode(
                nodeId, portName, index.getNodeImplementation(nodeId));
    }

    @Nullable
    private Object resolveConnection(int targetNodeId,
                                     CompiledDataIndex.DataConnectionSource source,
                                     RuntimeAdapter runtime) {
        Object value = evaluateOutput(source.sourceNodeId(), source.sourcePortKey(), runtime);
        return PortConversionRegistry.convert(value, source.sourceType(), source.targetType(),
                runtime.contextFor(targetNodeId));
    }

    public interface RuntimeAdapter {
        GraphDataContext contextFor(int nodeId);

        @Nullable
        Object computeNode(int nodeId, String outputPort, @Nullable BaseNode implementation);
    }
}
