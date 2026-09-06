package com.mine.geometry_node.core.engine.graph.compile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.graph.GraphDocumentType;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledGraph;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeRegistry;

import java.io.Reader;
import java.io.StringReader;
import java.util.Objects;

/** Resolves graph identity before dispatching to a runtime-owned compiler. */
public final class GraphCompilationService {
    public static final GraphCompilationService INSTANCE = new GraphCompilationService();

    private GraphCompilationService() {
    }

    public CompiledGraph compile(String json) {
        return compile(GraphCompileContext.ANONYMOUS,
                new StringReader(Objects.requireNonNull(json, "json")));
    }

    public CompiledGraph compile(String assetId, String json) {
        return compile(new GraphCompileContext(assetId),
                new StringReader(Objects.requireNonNull(json, "json")));
    }

    public CompiledGraph compile(Reader reader) {
        return compile(GraphCompileContext.ANONYMOUS, reader);
    }

    public CompiledGraph compile(String assetId, Reader reader) {
        return compile(new GraphCompileContext(assetId), reader);
    }

    private CompiledGraph compile(GraphCompileContext context, Reader reader) {
        JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
        return compile(context, document);
    }

    public CompiledGraph compile(JsonObject document) {
        return compile(GraphCompileContext.ANONYMOUS, document);
    }

    public CompiledGraph compile(String assetId, JsonObject document) {
        return compile(new GraphCompileContext(assetId), document);
    }

    private CompiledGraph compile(GraphCompileContext context, JsonObject document) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(document, "document");
        GraphType type = GraphDocumentType.require(document);
        GraphCompiler<? extends CompiledGraph> compiler =
                GraphRuntimeRegistry.INSTANCE.requireCompiler(type.runtimeKind());
        CompiledGraph result = compiler.compile(context, document);
        if (!type.id().equals(result.graphTypeId()) || type.runtimeKind() != result.runtimeKind()) {
            throw new IllegalStateException("Compiler returned an artifact with mismatched graph identity: " + type.id());
        }
        return result;
    }
}
