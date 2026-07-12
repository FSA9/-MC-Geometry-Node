package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Equal extends BaseNode {

    public static final String TYPE_ID = "equal";
    private static final String PORT_A = "A";
    private static final String PORT_B = "B";
    private static final String COUNT_MODE = "count_mode";

    private static final String MODE_TYPE_ONLY = "type_only";
    private static final String MODE_REGISTRY_ID = "registry_id";
    private static final String MODE_COMPONENTS = "components";
    private static final String MODE_EXACT = "exact";
    private static final String[] MODE_OPTIONS = {MODE_TYPE_ONLY, MODE_REGISTRY_ID, MODE_COMPONENTS, MODE_EXACT};

    private static final String COUNT_IGNORE = "ignore_count";
    private static final String COUNT_EXACT = "exact_count";
    private static final String COUNT_AT_LEAST = "at_least";
    private static final String COUNT_AT_MOST = "at_most";
    private static final String[] COUNT_OPTIONS = {COUNT_IGNORE, COUNT_EXACT, COUNT_AT_LEAST, COUNT_AT_MOST};

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断两个值是否相等。
                支持类型、注册 ID、组件和严格比较四种模式。
                物品栈默认比较物品类型和组件，忽略数量。
                数量模式仅对物品栈生效。""";

        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.equal"))
                .comment(comment)
                .addRow(new PortRow(
                        new PortDef(PORT_A, Component.literal("A"), PortType.ANY, null, false),
                        StandardPorts.BOOL.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        new PortDef(PORT_B, Component.literal("B"), PortType.ANY, null, false),
                        null,
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.TYPE.toInput(MODE_COMPONENTS).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, MODE_OPTIONS)
                ))
                .addRow(new PortRow(
                        PortDef.create(COUNT_MODE, "geometry_node.port.count_mode", PortType.STRING, COUNT_IGNORE).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, COUNT_OPTIONS)
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Object a = getRawInput(context, PORT_A);
        Object b = getRawInput(context, PORT_B);
        String mode = getInput(context, StandardPorts.TYPE.getId(), String.class);
        String countMode = getInput(context, COUNT_MODE, String.class);

        return valuesEqual(a, b, mode, countMode, context);
    }

    static boolean valuesEqual(Object a, Object b, String rawMode, String rawCountMode, ExecutionContext context) {
        a = _ValueTagSupport.unwrap(a);
        b = _ValueTagSupport.unwrap(b);

        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        String mode = normalizeMode(rawMode);
        boolean baseMatch = switch (mode) {
            case MODE_TYPE_ONLY -> typeOnlyEqual(a, b, context);
            case MODE_REGISTRY_ID -> registryIdEqual(a, b, context);
            case MODE_EXACT -> exactEqual(a, b);
            default -> componentsEqual(a, b, context);
        };
        if (!baseMatch) {
            return false;
        }

        return countMatches(a, b, normalizeCountMode(rawCountMode), mode);
    }

    private static boolean typeOnlyEqual(Object a, Object b, ExecutionContext context) {
        Set<String> left = _ValueTagSupport.kindKeys(a, context);
        Set<String> right = _ValueTagSupport.kindKeys(b, context);
        return intersects(left, right);
    }

    private static boolean registryIdEqual(Object a, Object b, ExecutionContext context) {
        Set<String> left = _ValueTagSupport.registryIdentities(a, context);
        Set<String> right = _ValueTagSupport.registryIdentities(b, context);
        return !left.isEmpty() && intersects(left, right);
    }

    private static boolean componentsEqual(Object a, Object b, ExecutionContext context) {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue()) == 0;
        }
        if (a instanceof Entity entA && b instanceof Entity entB) {
            return entA.getUUID().equals(entB.getUUID());
        }
        if (a instanceof ItemStack stackA && b instanceof ItemStack stackB) {
            return ItemStack.isSameItemSameComponents(stackA, stackB);
        }

        Set<String> left = _ValueTagSupport.registryIdentities(a, context);
        Set<String> right = _ValueTagSupport.registryIdentities(b, context);
        if (!left.isEmpty() || !right.isEmpty()) {
            return intersects(left, right);
        }

        return Objects.equals(a, b);
    }

    private static boolean exactEqual(Object a, Object b) {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue()) == 0;
        }
        if (a instanceof Entity entA && b instanceof Entity entB) {
            return entA.getUUID().equals(entB.getUUID());
        }
        if (a instanceof ItemStack stackA && b instanceof ItemStack stackB) {
            return ItemStack.matches(stackA, stackB);
        }
        return Objects.equals(a, b);
    }

    private static boolean countMatches(Object a, Object b, String countMode, String compareMode) {
        if (MODE_EXACT.equals(compareMode) || COUNT_IGNORE.equals(countMode)) {
            return true;
        }
        if (!(a instanceof ItemStack stackA) || !(b instanceof ItemStack stackB)) {
            return true;
        }

        int left = stackA.getCount();
        int right = stackB.getCount();
        return switch (countMode) {
            case COUNT_EXACT -> left == right;
            case COUNT_AT_LEAST -> left >= right;
            case COUNT_AT_MOST -> left <= right;
            default -> true;
        };
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMode(String rawMode) {
        if (rawMode == null) {
            return MODE_COMPONENTS;
        }
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case MODE_TYPE_ONLY, MODE_REGISTRY_ID, MODE_EXACT -> mode;
            default -> MODE_COMPONENTS;
        };
    }

    private static String normalizeCountMode(String rawCountMode) {
        if (rawCountMode == null) {
            return COUNT_IGNORE;
        }
        String mode = rawCountMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case COUNT_EXACT, COUNT_AT_LEAST, COUNT_AT_MOST -> mode;
            default -> COUNT_IGNORE;
        };
    }
}
