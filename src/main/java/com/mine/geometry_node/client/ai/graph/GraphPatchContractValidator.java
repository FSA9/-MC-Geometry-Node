package com.mine.geometry_node.client.ai.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates reference ordering and alias ownership before graph-aware planning. */
public final class GraphPatchContractValidator {
    private GraphPatchContractValidator() {}

    public record Diagnostic(int operationIndex, String code, String message) {}

    public static List<Diagnostic> validate(GraphPatch patch) {
        Set<String> nodeAliases = new HashSet<>();
        Set<String> frameAliases = new HashSet<>();
        Set<String> branchAliases = new HashSet<>();
        Set<String> portAliases = new HashSet<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < patch.operations().size(); index++) {
            GraphPatch.Operation operation = patch.operations().get(index);
            validateReferences(operation, nodeAliases, frameAliases, branchAliases, portAliases,
                    index, diagnostics);
            if (operation instanceof GraphPatch.AddNode value && !nodeAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_node_alias", "node alias is already declared: " + value.alias()));
            }
            if (operation instanceof GraphPatch.AddFrame value && !frameAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_frame_alias", "frame alias is already declared: " + value.alias()));
            }
            if (operation instanceof GraphPatch.AddDynamicBranch value && !branchAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_branch_alias", "branch alias is already declared: " + value.alias()));
            }
            if (operation instanceof GraphPatch.AddGroupVirtualPort value && !portAliases.add(value.alias())) {
                diagnostics.add(new Diagnostic(index, "patch.duplicate_port_alias", "port alias is already declared: " + value.alias()));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validateReferences(GraphPatch.Operation operation, Set<String> nodeAliases,
                                           Set<String> frameAliases, Set<String> branchAliases,
                                           Set<String> portAliases, int index,
                                           List<Diagnostic> diagnostics) {
        switch (operation) {
            case GraphPatch.RemoveNode value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.MoveNode value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.Connect value -> {
                validatePortRef(value.from(), nodeAliases, branchAliases, portAliases, index, diagnostics);
                validatePortRef(value.to(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            }
            case GraphPatch.Disconnect value -> {
                validatePortRef(value.from(), nodeAliases, branchAliases, portAliases, index, diagnostics);
                validatePortRef(value.to(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            }
            case GraphPatch.SetPortValue value -> validatePortRef(value.port(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            case GraphPatch.SetSelectValue value -> validatePortRef(value.port(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            case GraphPatch.SetNodeProperty value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RemoveFrame value -> validateFrameRef(value.frame(), frameAliases, index, diagnostics);
            case GraphPatch.SetFrameProperty value -> validateFrameRef(value.frame(), frameAliases, index, diagnostics);
            case GraphPatch.AddDynamicBranch value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RemoveDynamicBranch value -> {
                if (value.branch().alias() != null) {
                    if (!branchAliases.contains(value.branch().alias())) {
                        diagnostics.add(new Diagnostic(index, "patch.unknown_branch_alias",
                                "branch alias must be declared by an earlier operation: " + value.branch().alias()));
                    }
                } else {
                    validateNodeRef(value.branch().node(), nodeAliases, index, diagnostics);
                }
            }
            case GraphPatch.AddGroupVirtualPort value -> validateNodeRef(value.node(), nodeAliases, index, diagnostics);
            case GraphPatch.RemoveGroupVirtualPort value -> validatePortRef(value.port(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            case GraphPatch.RenamePort value -> validatePortRef(value.port(), nodeAliases, branchAliases, portAliases, index, diagnostics);
            default -> { }
        }
    }

    private static void validatePortRef(GraphPatch.PortRef ref, Set<String> nodeAliases,
                                        Set<String> branchAliases, Set<String> portAliases,
                                        int index, List<Diagnostic> diagnostics) {
        if (ref.alias() == null) {
            validateNodeRef(ref.node(), nodeAliases, index, diagnostics);
            return;
        }
        if (portAliases.contains(ref.alias())) return;
        int separator = ref.alias().indexOf('.');
        if (separator > 0 && separator < ref.alias().length() - 1
                && separator == ref.alias().lastIndexOf('.')
                && branchAliases.contains(ref.alias().substring(0, separator))) return;
        diagnostics.add(new Diagnostic(index, "patch.unknown_port_alias",
                "port alias must be declared by an earlier operation: " + ref.alias()));
    }

    private static void validateNodeRef(GraphPatch.NodeRef ref, Set<String> aliases, int index,
                                        List<Diagnostic> diagnostics) {
        if (ref.alias() != null && !aliases.contains(ref.alias())) {
            diagnostics.add(new Diagnostic(index, "patch.unknown_alias",
                    "alias must be declared by an earlier operation: " + ref.alias()));
        }
    }

    private static void validateFrameRef(GraphPatch.FrameRef ref, Set<String> aliases, int index,
                                         List<Diagnostic> diagnostics) {
        if (ref.alias() != null && !aliases.contains(ref.alias())) {
            diagnostics.add(new Diagnostic(index, "patch.unknown_frame_alias",
                    "frame alias must be declared by an earlier operation: " + ref.alias()));
        }
    }
}
