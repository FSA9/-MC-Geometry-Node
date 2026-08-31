package com.mine.geometry_node.core.engine.graph.expression;

import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Type-safe live-expression factories for the supported port types. */
public final class LiveValues {
    private LiveValues() {
    }

    public static LiveValue<Float> captureFloat(PortDef port, float snapshot, ExpressionSpec expression) {
        float initial = Float.isFinite(snapshot) ? snapshot : 0.0f;
        CompiledScalar compiled = compile(accepts(port, PortType.FLOAT) ? expression : null);
        return new ScalarLiveValue<>(initial, compiled, LiveValues::toFloat);
    }

    public static LiveValue<Integer> captureInteger(PortDef port, int snapshot, ExpressionSpec expression) {
        return new ScalarLiveValue<>(snapshot,
                compile(accepts(port, PortType.INTEGER) ? expression : null), LiveValues::toInteger);
    }

    public static LiveValue<Vec3> captureXyz(PortDef port, Vec3 snapshot, ExpressionSpec xExpression,
                                             ExpressionSpec yExpression, ExpressionSpec zExpression) {
        Vec3 initial = sanitize(snapshot);
        if (!accepts(port, PortType.XYZ)) {
            return new VectorLiveValue(initial, CompiledScalar.CONSTANT, CompiledScalar.CONSTANT,
                    CompiledScalar.CONSTANT);
        }
        return new VectorLiveValue(initial, compile(xExpression), compile(yExpression), compile(zExpression));
    }

    private static boolean accepts(PortDef port, PortType expectedType) {
        return port != null && port.type() == expectedType && port.liveExpressionEnabled();
    }

    private static CompiledScalar compile(ExpressionSpec expression) {
        if (expression == null) {
            return CompiledScalar.CONSTANT;
        }
        ExpressionProgram.Compilation compilation = ExpressionProgram.compile(expression);
        return new CompiledScalar(compilation.program(), compilation.diagnostic());
    }

    private static Float toFloat(double value, Float previous) {
        float converted = (float) value;
        return Double.isFinite(value) && Float.isFinite(converted) ? converted : previous;
    }

    private static Integer toInteger(double value, Integer previous) {
        if (!Double.isFinite(value)) {
            return previous;
        }
        double rounded = Math.copySign(Math.floor(Math.abs(value) + 0.5), value);
        if (rounded >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (rounded <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) rounded;
    }

    private static Vec3 sanitize(Vec3 value) {
        if (value == null) {
            return Vec3.ZERO;
        }
        return new Vec3(finiteOrZero(value.x), finiteOrZero(value.y), finiteOrZero(value.z));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    @FunctionalInterface
    private interface ScalarConverter<T> {
        T convert(double value, T previous);
    }

    private record CompiledScalar(ExpressionProgram program, String diagnostic) {
        private static final CompiledScalar CONSTANT = new CompiledScalar(null, "");

        private CompiledScalar {
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        private boolean dynamic() {
            return program != null;
        }
    }

    private record ScalarLiveValue<T>(T snapshot, CompiledScalar compiled, ScalarConverter<T> converter)
            implements LiveValue<T> {
        @Override
        public List<String> diagnostics() {
            return compiled.diagnostic().isEmpty() ? List.of() : List.of(compiled.diagnostic());
        }

        @Override
        public State<T> newState() {
            if (!compiled.dynamic()) {
                return new ConstantState<>(snapshot);
            }
            return new ScalarState<>(snapshot, compiled.program(), converter);
        }
    }

    private static final class ScalarState<T> implements LiveValue.State<T> {
        private final ExpressionProgram program;
        private final ScalarConverter<T> converter;
        private final double[] workspace;
        private T value;

        private ScalarState(T initial, ExpressionProgram program, ScalarConverter<T> converter) {
            this.value = initial;
            this.program = program;
            this.converter = converter;
            this.workspace = new double[program.workspaceSize()];
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public T evaluate(ExpressionEvaluationContext context) {
            try {
                value = converter.convert(program.evaluate(context, workspace), value);
            } catch (RuntimeException ignored) {
                // A transient binding failure keeps the last valid value for this consumer.
            }
            return value;
        }
    }

    private record VectorLiveValue(Vec3 snapshot, CompiledScalar x, CompiledScalar y, CompiledScalar z)
            implements LiveValue<Vec3> {
        @Override
        public List<String> diagnostics() {
            List<String> diagnostics = new ArrayList<>(3);
            addDiagnostic(diagnostics, "x", x.diagnostic());
            addDiagnostic(diagnostics, "y", y.diagnostic());
            addDiagnostic(diagnostics, "z", z.diagnostic());
            return List.copyOf(diagnostics);
        }

        @Override
        public State<Vec3> newState() {
            return new VectorState(snapshot, x, y, z);
        }
    }

    private static final class VectorState implements LiveValue.State<Vec3> {
        private final AxisState x;
        private final AxisState y;
        private final AxisState z;
        private Vec3 value;

        private VectorState(Vec3 initial, CompiledScalar x, CompiledScalar y, CompiledScalar z) {
            this.x = new AxisState(initial.x, x.program());
            this.y = new AxisState(initial.y, y.program());
            this.z = new AxisState(initial.z, z.program());
            this.value = initial;
        }

        @Override
        public Vec3 value() {
            return value;
        }

        @Override
        public Vec3 evaluate(ExpressionEvaluationContext context) {
            double nextX = x.evaluate(context);
            double nextY = y.evaluate(context);
            double nextZ = z.evaluate(context);
            if (nextX != value.x || nextY != value.y || nextZ != value.z) {
                value = new Vec3(nextX, nextY, nextZ);
            }
            return value;
        }
    }

    private static final class AxisState {
        private final ExpressionProgram program;
        private final double[] workspace;
        private double value;

        private AxisState(double initial, ExpressionProgram program) {
            this.value = initial;
            this.program = program;
            this.workspace = program == null ? null : new double[program.workspaceSize()];
        }

        private double evaluate(ExpressionEvaluationContext context) {
            if (program == null) {
                return value;
            }
            try {
                double next = program.evaluate(context, workspace);
                if (Double.isFinite(next)) {
                    value = next;
                }
            } catch (RuntimeException ignored) {
                // Each XYZ axis falls back independently.
            }
            return value;
        }
    }

    private record ConstantState<T>(T value) implements LiveValue.State<T> {
        @Override
        public T evaluate(ExpressionEvaluationContext context) {
            return value;
        }
    }

    private static void addDiagnostic(List<String> diagnostics, String axis, String diagnostic) {
        if (!diagnostic.isEmpty()) {
            diagnostics.add(axis + ": " + diagnostic);
        }
    }
}
