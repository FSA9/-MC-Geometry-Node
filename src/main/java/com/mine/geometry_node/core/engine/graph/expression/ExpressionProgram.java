package com.mine.geometry_node.core.engine.graph.expression;

import com.mine.geometry_node.core.utils.expression.ASTNode;
import com.mine.geometry_node.core.utils.expression.ExpressionCompiler;
import com.mine.geometry_node.core.utils.expression.VariableRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable compiled expression. Mutable evaluation memory belongs to each live-value state. */
final class ExpressionProgram {
    private static final int COMPILED_FORMULA_CACHE_LIMIT = 1024;
    private static final Map<String, CompiledFormula> COMPILED_FORMULAS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompiledFormula> eldest) {
            return size() > COMPILED_FORMULA_CACHE_LIMIT;
        }
    };

    private final ASTNode root;
    private final VariableSource[] sources;
    private final ExpressionBinding[] bindings;

    private ExpressionProgram(ExpressionSpec spec, CompiledFormula compiled) {
        this.root = compiled.root();
        this.sources = new VariableSource[compiled.variableCount()];
        this.bindings = new ExpressionBinding[compiled.variableCount()];
        compiled.variableIndexes().forEach((name, index) -> {
            if ("tick".equalsIgnoreCase(name)) {
                sources[index] = VariableSource.WORLD_GAME_TIME;
            } else if ("age".equalsIgnoreCase(name)) {
                sources[index] = VariableSource.RESOURCE_AGE;
            } else {
                sources[index] = VariableSource.BINDING;
                bindings[index] = spec.bindings().get(name);
            }
        });
    }

    static Compilation compile(ExpressionSpec spec) {
        if (spec == null) {
            return new Compilation(null, "Expression specification is missing");
        }
        try {
            CompiledFormula compiled = compiledFormula(spec.formula());
            return new Compilation(new ExpressionProgram(spec, compiled), "");
        } catch (ExpressionCompiler.ExpressionSyntaxException exception) {
            return new Compilation(null, exception.getMessage());
        }
    }

    int workspaceSize() {
        return sources.length;
    }

    double evaluate(ExpressionEvaluationContext context, double[] workspace) {
        if (workspace.length < sources.length) {
            throw new IllegalArgumentException("Expression workspace is too small");
        }
        for (int index = 0; index < sources.length; index++) {
            workspace[index] = switch (sources[index]) {
                case WORLD_GAME_TIME -> context.worldGameTime();
                case RESOURCE_AGE -> context.resourceAge();
                case BINDING -> resolveBinding(context, bindings[index]);
            };
        }
        return root.evaluate(workspace);
    }

    private static double resolveBinding(ExpressionEvaluationContext context, ExpressionBinding binding) {
        return binding == null ? 0.0 : context.resolve(binding);
    }

    private static CompiledFormula compiledFormula(String formula) {
        synchronized (COMPILED_FORMULAS) {
            CompiledFormula cached = COMPILED_FORMULAS.get(formula);
            if (cached != null) {
                return cached;
            }
        }

        VariableRegistry registry = new VariableRegistry();
        ASTNode root = ExpressionCompiler.compileStrict(formula, registry);
        CompiledFormula compiled = new CompiledFormula(root, Map.copyOf(registry.getMapping()),
                registry.getVarCount());
        synchronized (COMPILED_FORMULAS) {
            CompiledFormula existing = COMPILED_FORMULAS.get(formula);
            if (existing != null) {
                return existing;
            }
            COMPILED_FORMULAS.put(formula, compiled);
        }
        return compiled;
    }

    record Compilation(ExpressionProgram program, String diagnostic) {
        public Compilation {
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    private enum VariableSource {
        WORLD_GAME_TIME,
        RESOURCE_AGE,
        BINDING
    }

    private record CompiledFormula(ASTNode root, Map<String, Integer> variableIndexes, int variableCount) {
    }
}
