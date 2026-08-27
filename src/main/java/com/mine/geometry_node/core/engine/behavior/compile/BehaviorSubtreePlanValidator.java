package com.mine.geometry_node.core.engine.behavior.compile;

import com.mine.geometry_node.core.engine.behavior.contract.BlackboardScope;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorSubtreeParameter;
import com.mine.geometry_node.core.engine.behavior.document.BehaviorTreeDiagnostic;
import com.mine.geometry_node.core.engine.behavior.plan.BehaviorTreePlan;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Validates a compiled subtree call graph against the current effective asset view. */
public final class BehaviorSubtreePlanValidator {
    private BehaviorSubtreePlanValidator() {
    }

    public static List<BehaviorTreeDiagnostic> validate(
            BehaviorTreePlan root, Function<String, @Nullable BehaviorTreePlan> resolver) {
        List<BehaviorTreeDiagnostic> diagnostics = new ArrayList<>();
        Deque<String> path = new ArrayDeque<>();
        Set<String> validated = new HashSet<>();
        validatePlan(root, resolver, path, validated, diagnostics);
        return List.copyOf(diagnostics);
    }

    /** Validates only this plan's call sites; lifecycle indexes handle graph traversal. */
    public static List<BehaviorTreeDiagnostic> validateDirectDependencies(
            BehaviorTreePlan plan, Function<String, @Nullable BehaviorTreePlan> resolver) {
        List<BehaviorTreeDiagnostic> diagnostics = new ArrayList<>();
        for (BehaviorTreePlan.SubtreeDependency call : plan.dependencyManifest().dependencies()) {
            BehaviorTreePlan target = resolver.apply(call.assetId());
            if (target == null) {
                diagnostics.add(diagnostic(plan, call, "SUBTREE_DEPENDENCY_MISSING",
                        "Subtree asset is unavailable in the effective asset view"));
            } else if (target.getRootNode() < 0) {
                diagnostics.add(diagnostic(plan, call, "SUBTREE_ROOT_MISSING",
                        "Subtree asset has no executable Root node"));
            } else {
                validateMappings(plan, call, target, diagnostics);
            }
        }
        return List.copyOf(diagnostics);
    }

    private static void validatePlan(
            BehaviorTreePlan plan, Function<String, @Nullable BehaviorTreePlan> resolver,
            Deque<String> path, Set<String> validated,
            List<BehaviorTreeDiagnostic> diagnostics) {
        String assetId = plan.assetId();
        path.addLast(assetId);
        for (BehaviorTreePlan.SubtreeDependency call : plan.dependencyManifest().dependencies()) {
            BehaviorTreePlan target = resolver.apply(call.assetId());
            if (target == null) {
                diagnostics.add(diagnostic(plan, call, "SUBTREE_DEPENDENCY_MISSING",
                        "Subtree asset is unavailable in the effective asset view"));
                continue;
            }
            if (target.getRootNode() < 0) {
                diagnostics.add(diagnostic(plan, call, "SUBTREE_ROOT_MISSING",
                        "Subtree asset has no executable Root node"));
                continue;
            }
            validateMappings(plan, call, target, diagnostics);
            if (path.contains(call.assetId())) {
                diagnostics.add(diagnostic(plan, call, "SUBTREE_RECURSION",
                        "Subtree call introduces a recursive dependency: "
                                + String.join(" -> ", path) + " -> " + call.assetId()));
                continue;
            }
            if (!validated.contains(call.assetId())) {
                validatePlan(target, resolver, path, validated, diagnostics);
            }
        }
        path.removeLast();
        validated.add(assetId);
    }

    private static void validateMappings(
            BehaviorTreePlan caller, BehaviorTreePlan.SubtreeDependency call,
            BehaviorTreePlan target, List<BehaviorTreeDiagnostic> diagnostics) {
        call.inputMapping().forEach((parameterName, callerKeyName) -> {
            BehaviorTreePlan.SubtreeParameter parameter = target.subtreeSignature().find(parameterName);
            BehaviorTreePlan.BlackboardKey callerKey = caller.blackboardSchema()
                    .find(BlackboardScope.INSTANCE, callerKeyName);
            if (parameter == null || parameter.direction() != BehaviorSubtreeParameter.Direction.INPUT) {
                diagnostics.add(diagnostic(caller, call, "SUBTREE_INPUT_PARAMETER_MISSING",
                        "Subtree input mapping references an unavailable INPUT parameter: "
                                + parameterName));
            } else if (callerKey == null || callerKey.type() != parameter.type()) {
                diagnostics.add(diagnostic(caller, call, "SUBTREE_INPUT_TYPE_MISMATCH",
                        "Subtree input mapping types do not match: " + callerKeyName
                                + " -> " + parameterName));
            }
        });
        call.outputMapping().forEach((callerKeyName, parameterName) -> {
            BehaviorTreePlan.SubtreeParameter parameter = target.subtreeSignature().find(parameterName);
            BehaviorTreePlan.BlackboardKey callerKey = caller.blackboardSchema()
                    .find(BlackboardScope.INSTANCE, callerKeyName);
            if (parameter == null || parameter.direction() != BehaviorSubtreeParameter.Direction.OUTPUT) {
                diagnostics.add(diagnostic(caller, call, "SUBTREE_OUTPUT_PARAMETER_MISSING",
                        "Subtree output mapping references an unavailable OUTPUT parameter: "
                                + parameterName));
            } else if (callerKey == null || !callerKey.writable()
                    || callerKey.type() != parameter.type()) {
                diagnostics.add(diagnostic(caller, call, "SUBTREE_OUTPUT_TYPE_MISMATCH",
                        "Subtree output mapping types do not match: " + parameterName
                                + " -> " + callerKeyName));
            }
        });
    }

    private static BehaviorTreeDiagnostic diagnostic(
            BehaviorTreePlan caller, BehaviorTreePlan.SubtreeDependency call,
            String code, String message) {
        return new BehaviorTreeDiagnostic(caller.assetId(), code, message,
                call.callNodeId(), "", call.assetId());
    }
}
