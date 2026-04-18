package com.mine.geometry_node.core.node.nodes.data;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TargetSelector extends BaseNode {

    public static final String TYPE_ID = "target_selector";

    private static final String[] BASE_TARGETS = {"@e", "@a", "@p", "@r", "@s"};

    // 包含 1.21.1 中除 NBT/Scores/Advancements/Predicate 以外的所有参数
    private static final List<String> ALL_FILTERS = List.of(
            "none", "center", "volume", "distance", "type", "tag", "team",
            "limit", "sort", "level", "gamemode", "name", "x_rotation", "y_rotation"
    );

    private static final String[] SORT_OPTIONS = {"nearest", "furthest", "random", "arbitrary"};
    private static final String[] GAMEMODE_OPTIONS = {"survival", "creative", "adventure", "spectator"};

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.literal("geometry_node.node.target_selector"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);

        // 输出端口
        builder.addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null));

        // 基础选择器 (@e, @a...)
        builder.addRow(new PortRow(
                null, null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.BIND_PROPERTY, "base_target", PortMetaKeys.OPTIONS, BASE_TARGETS)
        ));

        int filterCount = 1;
        List<String> usedFilters = new ArrayList<>();

        if (instanceData != null) {
            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
            if (countObj instanceof Number n) {
                filterCount = Math.max(1, n.intValue());
            }
            for (int i = 1; i <= filterCount; i++) {
                String f = (String) instanceData.properties.get("filter_type_" + i);
                if (f != null && !f.equals("none")) usedFilters.add(f);
            }
        }

        for (int i = 1; i <= filterCount; i++) {
            String filterTypeProp = "filter_type_" + i;
            String currentFilter = instanceData != null
                    ? (String) instanceData.properties.getOrDefault(filterTypeProp, "none")
                    : "none";

            List<String> availableOptions = new ArrayList<>(ALL_FILTERS);
            availableOptions.removeAll(usedFilters);
            if (!availableOptions.contains(currentFilter)) availableOptions.add(currentFilter);

            builder.addRow(new PortRow(
                    null, null, UIHint.SELECT, null,
                    Map.of(
                            PortMetaKeys.BIND_PROPERTY, filterTypeProp,
                            PortMetaKeys.OPTIONS, availableOptions.toArray(new String[0]),
                            PortMetaKeys.IS_DYNAMIC, true
                    )
            ));

            for (PortRow row : createDataRows(currentFilter, i)) {
                builder.addRow(row);
            }
        }

        return builder.build();
    }

    private List<PortRow> createDataRows(String type, int index) {
        return switch (type) {
            case "none" -> List.of();
            case "center" -> List.of(new PortRow(
                    StandardPorts.CENTER.toInputWithIndex(index), null, UIHint.VECTOR, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)
            ));
            case "volume" -> List.of(new PortRow(
                    StandardPorts.SIZE_3.toInputWithIndex(index), null, UIHint.VECTOR, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)
            ));
            case "distance", "x_rotation", "y_rotation" -> List.of(
                    new PortRow(StandardPorts.MIN_FLOAT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)),
                    new PortRow(StandardPorts.MAX_FLOAT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
            case "level" -> List.of(
                    new PortRow(StandardPorts.MIN_INT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)),
                    new PortRow(StandardPorts.MAX_INT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
            case "type" -> List.of(new PortRow(
                    StandardPorts.TYPE.toInputWithIndex(index), null, UIHint.SELECT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:entity_type")
            ));
            case "sort" -> List.of(new PortRow(
                    StandardPorts.SORT.toInputWithIndex(index), null, UIHint.SELECT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.OPTIONS, SORT_OPTIONS)
            ));
            case "gamemode" -> List.of(new PortRow(
                    StandardPorts.GAMEMODE.toInputWithIndex(index), null, UIHint.SELECT, null,
                    Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.OPTIONS, GAMEMODE_OPTIONS)
            ));
            case "tag" -> List.of(new PortRow(StandardPorts.TAG.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            case "name" -> List.of(new PortRow(StandardPorts.NAME.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            case "team" -> List.of(new PortRow(StandardPorts.TEAM.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            case "limit" -> List.of(new PortRow(StandardPorts.LIMIT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            default -> List.of();
        };
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.LIST.getId().equals(portName)) return null;

        String base = (String) context.getNodeProperty("base_target");
        if (base == null) base = "@e";

        List<String> arguments = new ArrayList<>();
        int filterCount = 1;
        Object countObj = context.getNodeProperty(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
        if (countObj instanceof Number n) filterCount = Math.max(1, n.intValue());

        for (int i = 1; i <= filterCount; i++) {
            String filterType = (String) context.getNodeProperty("filter_type_" + i);
            if (filterType == null || filterType.equals("none")) continue;

            switch (filterType) {
                case "center" -> {
                    List<Float> pos = getInputList(context, StandardPorts.CENTER.getIdWithIndex(i), Float.class);
                    if (pos != null && pos.size() >= 3) {
                        arguments.add("x=" + formatNum(pos.get(0)));
                        arguments.add("y=" + formatNum(pos.get(1)));
                        arguments.add("z=" + formatNum(pos.get(2)));
                    }
                }
                case "volume" -> {
                    List<Float> dim = getInputList(context, StandardPorts.SIZE_3.getIdWithIndex(i), Float.class);
                    if (dim != null && dim.size() >= 3) {
                        arguments.add("dx=" + formatNum(dim.get(0)));
                        arguments.add("dy=" + formatNum(dim.get(1)));
                        arguments.add("dz=" + formatNum(dim.get(2)));
                    }
                }
                case "distance", "x_rotation", "y_rotation" -> {
                    Float min = getInput(context, StandardPorts.MIN_FLOAT.getIdWithIndex(i), Float.class);
                    Float max = getInput(context, StandardPorts.MAX_FLOAT.getIdWithIndex(i), Float.class);
                    String range = formatRange(min, max);
                    if (range != null) arguments.add(filterType + "=" + range);
                }
                case "level" -> {
                    Integer min = getInput(context, StandardPorts.MIN_INT.getIdWithIndex(i), Integer.class);
                    Integer max = getInput(context, StandardPorts.MAX_INT.getIdWithIndex(i), Integer.class);
                    String range = formatRange(min, max);
                    if (range != null) arguments.add("level=" + range);
                }
                case "limit" -> {
                    Integer limit = getInput(context, StandardPorts.LIMIT.getIdWithIndex(i), Integer.class);
                    if (limit != null) arguments.add("limit=" + limit);
                }
                default -> {
                    // 处理所有简单的字符串匹配参数 (type, tag, team, name, gamemode, sort)
                    StandardPorts port = switch (filterType) {
                        case "type" -> StandardPorts.TYPE;
                        case "tag" -> StandardPorts.TAG;
                        case "team" -> StandardPorts.TEAM;
                        case "name" -> StandardPorts.NAME;
                        case "gamemode" -> StandardPorts.GAMEMODE;
                        case "sort" -> StandardPorts.SORT;
                        default -> null;
                    };
                    if (port != null) {
                        String val = getInput(context, port.getIdWithIndex(i), String.class);
                        if (val != null && !val.isEmpty()) arguments.add(filterType + "=" + val);
                    }
                }
            }
        }

        StringBuilder selectorBuilder = new StringBuilder(base);
        if (!arguments.isEmpty()) {
            selectorBuilder.append("[").append(String.join(",", arguments)).append("]");
        }

        String finalSelector = selectorBuilder.toString();
        // 此处你可以根据生成的 finalSelector 字符串去获取实体列表
        System.out.println("[TargetSelector] 生成指令: " + finalSelector);

        return List.of();
    }

    private String formatRange(Number min, Number max) {
        if (min == null && max == null) return null;
        String minStr = min != null ? formatNum(min) : "";
        String maxStr = max != null ? formatNum(max) : "";
        if (minStr.equals(maxStr) && !minStr.isEmpty()) return minStr;
        return minStr + ".." + maxStr;
    }

    private String formatNum(Number n) {
        double v = n.doubleValue();
        return (v == (long) v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}