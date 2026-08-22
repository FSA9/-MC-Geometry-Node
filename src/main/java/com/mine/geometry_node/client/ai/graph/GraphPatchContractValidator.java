package com.mine.geometry_node.client.ai.graph;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Contract-level validation only; graph-aware validation belongs to the P5 planner. */
public final class GraphPatchContractValidator {
    private GraphPatchContractValidator() {}

    public record Diagnostic(int operationIndex, String code, String message) {}

    public static List<Diagnostic> validate(GraphPatch patch) {
        Set<String> nodeAliases = new HashSet<>();
        Set<String> frameAliases = new HashSet<>();
        java.util.ArrayList<Diagnostic> diagnostics = new java.util.ArrayList<>();
        for (int index = 0; index < patch.operations().size(); index++) {
            GraphPatch.Operation operation = patch.operations().get(index);
            validateReferences(operation, nodeAliases, frameAliases, index, diagnostics);
            if (operation instanceof GraphPatch.AddNode value && !nodeAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_node_alias", "node alias is already declared: " + value.alias()));
            }
            if (operation instanceof GraphPatch.AddFrame value && !frameAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_frame_alias", "frame alias is already declared: " + value.alias()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validateReferences(GraphPatch.Operation operation, Set<String> nodeAliases,
                                           Set<String> frameAliases, int index,
                                           List<Diagnostic> diagnostics) {
        for (GraphPatch.NodeRef ref : references(operation)) {
            if (ref.alias() != null && !nodeAliases.contains(ref.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.unknown_alias", "alias must be declared by an earlier operation: " + ref.alias()));
            }
        }
        if (operation instanceof GraphPatch.SetFrameProperty value
                && value.frame().alias() != null && !frameAliases.contains(value.frame().alias())) {
            diagnostics.add(new Diagnostic(index, "patch.unknown_frame_alias",
                    "frame alias must be declared by an earlier operation: " + value.frame().alias()));
        }
    }

    private static List<GraphPatch.NodeRef> references(GraphPatch.Operation operation) {
        return switch (operation) {
            case GraphPatch.RemoveNode value -> List.of(value.node());
            case GraphPatch.MoveNode value -> List.of(value.node());
            case GraphPatch.Connect value -> List.of(value.from().node(), value.to().node());
            case GraphPatch.Disconnect value -> List.of(value.from().node(), value.to().node());
            case GraphPatch.SetPortValue value -> List.of(value.port().node());
            case GraphPatch.SetSelectValue value -> List.of(value.port().node());
            case GraphPatch.SetNodeProperty value -> List.of(value.node());
            case GraphPatch.AddDynamicBranch value -> List.of(value.node());
            case GraphPatch.RemoveDynamicBranch value -> List.of(value.node());
            case GraphPatch.AddGroupVirtualPort value -> List.of(value.node());
            case GraphPatch.RemoveGroupVirtualPort value -> List.of(value.node());
            case GraphPatch.RenamePort value -> List.of(value.node());
            default -> List.of();
        };
    }
}
