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
        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.target_selector"))
                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);

        // 输出端口
        builder.addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null));

        builder.addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null));

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
                    // 【修复2】: VECTOR 端口传递的是 Vec3 对象，不能用 getInputList 强转为 List<Float>
                    net.minecraft.world.phys.Vec3 pos = getInput(context, StandardPorts.CENTER.getIdWithIndex(i), net.minecraft.world.phys.Vec3.class);
                    if (pos != null) {
                        arguments.add("x=" + formatNum(pos.x));
                        arguments.add("y=" + formatNum(pos.y));
                        arguments.add("z=" + formatNum(pos.z));
                    }
                }
                case "volume" -> {
                    // 【修复2】: 同上，改用 Vec3 接收
                    net.minecraft.world.phys.Vec3 dim = getInput(context, StandardPorts.SIZE_3.getIdWithIndex(i), net.minecraft.world.phys.Vec3.class);
                    if (dim != null) {
                        arguments.add("dx=" + formatNum(dim.x));
                        arguments.add("dy=" + formatNum(dim.y));
                        arguments.add("dz=" + formatNum(dim.z));
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

        try {
            net.minecraft.world.entity.Entity explicitEntity = getInput(context, StandardPorts.ENTITY.getId(), net.minecraft.world.entity.Entity.class);
            net.minecraft.commands.CommandSourceStack source;

            // --- 严格按照“输入实体 > 绑定对象”的优先级构建执行源 ---
            if (explicitEntity != null) {
                // 【修复1】: 强制提权到 Permission Level 4，否则普通玩家/实体作为 Source 无法执行 @a/@e
                source = explicitEntity.createCommandSourceStack().withPermission(4);
            }
            else if (context.getEntity() != null) {
                source = context.getEntity().createCommandSourceStack().withPermission(4);
            }
            else if (context.getLevel() != null) {
                net.minecraft.server.level.ServerLevel serverLevel = context.getLevel();
                source = serverLevel.getServer().createCommandSourceStack().withLevel(serverLevel).withPermission(4);
            }
            else {
                System.err.println("[TargetSelector] 无法获取任何有效的执行主体 (主语为空)");
                return java.util.List.of();
            }

            // 解析选择器并执行搜索
            com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(finalSelector);
            net.minecraft.commands.arguments.selector.EntitySelector selector =
                    new net.minecraft.commands.arguments.selector.EntitySelectorParser(reader, true).parse();

            return selector.findEntities(source);

        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            // 【优化】: 打印具体的 e.getMessage()，以后如果参数写错了(如队伍名不存在)，就能看到具体原因，而不是只看到一个 "@e[...]"
            System.err.println("[TargetSelector] 目标选择器语法错误: " + finalSelector + " | 详细原因: " + e.getMessage());
        }

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