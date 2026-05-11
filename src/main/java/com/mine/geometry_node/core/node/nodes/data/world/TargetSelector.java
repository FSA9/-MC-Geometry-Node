package com.mine.geometry_node.core.node.nodes.data.world;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.meta.PropertyKeys;
import com.mine.geometry_node.core.node.meta.SchemaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TargetSelector extends BaseNode {

    public static final String TYPE_ID = "target_selector";

    private static final String[] BASE_TARGETS = {"@e", "@a", "@p", "@r", "@s"};

    // 完美补齐 1.21.1 所有可用参数
    private static final List<String> ALL_FILTERS = List.of(
            "none", "center", "volume", "distance", "type", "tag", "team",
            "limit", "sort", "level", "gamemode", "name", "x_rotation", "y_rotation",
            "nbt", "scores", "advancements", "predicate" // 【新增】四大核心关键字
    );

    private static final String[] SORT_OPTIONS = {"nearest", "furthest", "random", "arbitrary"};
    private static final String[] GAMEMODE_OPTIONS = {"survival", "creative", "adventure", "spectator"};

    @Override
    public NodeDef getDefaultDefinition() {
        return getDefinition(null);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
//        NodeDef.Builder builder = NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.target_selector"))
//                .addMeta(SchemaKeys.MAX_DYNAMIC_INPUT, 30);
//
//        builder.addRow(new PortRow(null, StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null));
//        builder.addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null));
//
//        builder.addRow(new PortRow(
//                null, null, UIHint.SELECT, null,
//                Map.of(PortMetaKeys.BIND_PROPERTY, "base_target", PortMetaKeys.OPTIONS, BASE_TARGETS)
//        ));
//
//        int filterCount = 1;
//        List<String> usedFilters = new ArrayList<>();
//
//        if (instanceData != null) {
//            Object countObj = instanceData.properties.get(PropertyKeys.DYNAMIC_BRANCH_INPUT_COUNT.id());
//            if (countObj instanceof Number n) {
//                filterCount = Math.max(1, n.intValue());
//            }
//            for (int i = 1; i <= filterCount; i++) {
//                String f = (String) instanceData.properties.get("filter_type_" + i);
//                if (f != null && !f.equals("none")) usedFilters.add(f);
//            }
//        }
//
//        for (int i = 1; i <= filterCount; i++) {
//            String filterTypeProp = "filter_type_" + i;
//            String currentFilter = instanceData != null
//                    ? (String) instanceData.properties.getOrDefault(filterTypeProp, "none")
//                    : "none";
//
//            // 保持 removeAll 逻辑，因为现在所有支持多选的字段都改为直接接收 LIST 端口了
//            List<String> availableOptions = new ArrayList<>(ALL_FILTERS);
//            availableOptions.removeAll(usedFilters);
//            if (!availableOptions.contains(currentFilter)) availableOptions.add(currentFilter);
//
//            builder.addRow(new PortRow(
//                    null, null, UIHint.SELECT, null,
//                    Map.of(
//                            PortMetaKeys.BIND_PROPERTY, filterTypeProp,
//                            PortMetaKeys.OPTIONS, availableOptions.toArray(new String[0]),
//                            PortMetaKeys.IS_DYNAMIC, true
//                    )
//            ));
//
//            for (PortRow row : createDataRows(currentFilter, i)) {
//                builder.addRow(row);
//            }
//        }
//
//        return builder.build();
        return null;
    }

    private List<PortRow> createDataRows(String type, int index) {
        return switch (type) {
            case "none" -> List.of();
            case "center" -> List.of(new PortRow(StandardPorts.CENTER.toInputWithIndex(index), null, UIHint.VECTOR, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            case "volume" -> List.of(new PortRow(StandardPorts.SIZE_3.toInputWithIndex(index), null, UIHint.VECTOR, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)));
            case "distance", "x_rotation", "y_rotation" -> List.of(
                    new PortRow(StandardPorts.MIN_FLOAT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)),
                    new PortRow(StandardPorts.MAX_FLOAT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
            case "level" -> List.of(
                    new PortRow(StandardPorts.MIN_INT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true)),
                    new PortRow(StandardPorts.MAX_INT.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
            case "type" -> List.of(
                    // 【重构】使用下拉框选择类型，并在下方额外生成一个 Checkbox 用于取反
                    new PortRow(StandardPorts.TYPE.toInputWithIndex(index), null, UIHint.SELECT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.DYNAMIC_REGISTRY_ID, "minecraft:entity_type")),
                    new PortRow(StandardPorts.BOOL.toInputWithIndex(index, false), null, UIHint.CHECKBOX, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
//            // 【重构】将支持多重堆叠的标识符统一改为接收 LIST
//            case "tag", "name", "team" -> List.of(
//                    new PortRow(new PortDef(type + "_" + index, Component.literal(type.toUpperCase() + " List"), PortType.LIST, List.of()), null, UIHint.DEFAULT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
//            );
//            // 【新增】复合数据统一接收 DICT
//            case "nbt", "scores", "advancements" -> List.of(
//                    new PortRow(new PortDef(type + "_" + index, Component.literal(type.toUpperCase() + " Dict"), PortType.DICT, Map.of()), null, UIHint.DEFAULT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
//            );
            // 【新增】谓词接收单行 PREDICATE
            case "predicate" -> List.of(
                    new PortRow(StandardPorts.PREDICATE.toInputWithIndex(index), null, UIHint.INPUT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true))
            );
            case "sort" -> List.of(new PortRow(StandardPorts.SORT.toInputWithIndex(index), null, UIHint.SELECT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.OPTIONS, SORT_OPTIONS)));
            case "gamemode" -> List.of(new PortRow(StandardPorts.GAMEMODE.toInputWithIndex(index), null, UIHint.SELECT, null, Map.of(PortMetaKeys.IS_DYNAMIC, true, PortMetaKeys.OPTIONS, GAMEMODE_OPTIONS)));
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
                    net.minecraft.world.phys.Vec3 pos = getInput(context, StandardPorts.CENTER.getIdWithIndex(i), net.minecraft.world.phys.Vec3.class);
                    if (pos != null) {
                        arguments.add("x=" + formatNum(pos.x)); arguments.add("y=" + formatNum(pos.y)); arguments.add("z=" + formatNum(pos.z));
                    }
                }
                case "volume" -> {
                    net.minecraft.world.phys.Vec3 dim = getInput(context, StandardPorts.SIZE_3.getIdWithIndex(i), net.minecraft.world.phys.Vec3.class);
                    if (dim != null) {
                        arguments.add("dx=" + formatNum(dim.x)); arguments.add("dy=" + formatNum(dim.y)); arguments.add("dz=" + formatNum(dim.z));
                    }
                }
                case "distance", "x_rotation", "y_rotation", "level" -> {
                    String range = (filterType.equals("level")) ?
                            formatRange(getInput(context, StandardPorts.MIN_INT.getIdWithIndex(i), Integer.class), getInput(context, StandardPorts.MAX_INT.getIdWithIndex(i), Integer.class)) :
                            formatRange(getInput(context, StandardPorts.MIN_FLOAT.getIdWithIndex(i), Float.class), getInput(context, StandardPorts.MAX_FLOAT.getIdWithIndex(i), Float.class));
                    if (range != null) arguments.add(filterType + "=" + range);
                }
                case "limit" -> {
                    Integer limit = getInput(context, StandardPorts.LIMIT.getIdWithIndex(i), Integer.class);
                    if (limit != null) arguments.add("limit=" + limit);
                }
                case "type" -> {
                    // 解析下拉框的类型值，以及下方复选框的布尔值
                    String typeVal = getInput(context, StandardPorts.TYPE.getIdWithIndex(i), String.class);
                    Boolean negate = getInput(context, StandardPorts.BOOL.getIdWithIndex(i), Boolean.class);
                    if (typeVal != null && !typeVal.isEmpty()) {
                        arguments.add("type=" + (Boolean.TRUE.equals(negate) ? "!" : "") + typeVal);
                    }
                }
                case "tag", "name", "team" -> {
                    // 动态获取输入的列表内容，并自动展开为多个独立的匹配项
                    List<String> list = getInputList(context, filterType + "_" + i, String.class);
                    for (String val : list) {
                        if (val != null && !val.isEmpty()) arguments.add(filterType + "=" + val);
                    }
                }
                case "nbt" -> {
                    Map<String, Object> dict = getInputDict(context, "nbt_" + i);
                    if (!dict.isEmpty() && context.getLevel() != null) {
                        // 【NBT 字符串提取魔法】复用 VariableRegistry 将字典序列化，然后扒出内部的 CompoundTag
                        Tag gnTag = VariableRegistry.toTag(dict, context.getLevel().registryAccess());
                        if (gnTag instanceof CompoundTag c && c.contains("data", Tag.TAG_COMPOUND)) {
                            // CompoundTag.toString() 天然就是 Minecraft 原版支持的标准 SNBT 格式！
                            arguments.add("nbt=" + c.getCompound("data").toString());
                        }
                    }
                }
                case "scores", "advancements" -> {
                    Map<String, Object> dict = getInputDict(context, filterType + "_" + i);
                    if (!dict.isEmpty()) {
                        List<String> entries = new ArrayList<>();
                        for (Map.Entry<String, Object> entry : dict.entrySet()) {
                            if (entry.getValue() != null) entries.add(entry.getKey() + "=" + entry.getValue().toString());
                        }
                        if (!entries.isEmpty()) arguments.add(filterType + "={" + String.join(",", entries) + "}");
                    }
                }
                case "predicate", "gamemode", "sort" -> {
                    String portId = filterType.equals("predicate") ? StandardPorts.PREDICATE.getIdWithIndex(i) :
                            filterType.equals("gamemode") ? StandardPorts.GAMEMODE.getIdWithIndex(i) : StandardPorts.SORT.getIdWithIndex(i);
                    String val = getInput(context, portId, String.class);
                    if (val != null && !val.isEmpty()) arguments.add(filterType + "=" + val);
                }
            }
        }

        String finalSelector = base + (!arguments.isEmpty() ? "[" + String.join(",", arguments) + "]" : "");

        try {
            net.minecraft.world.entity.Entity explicitEntity = getInput(context, StandardPorts.ENTITY.getId(), net.minecraft.world.entity.Entity.class);
            net.minecraft.commands.CommandSourceStack source;

            if (explicitEntity != null) source = explicitEntity.createCommandSourceStack().withPermission(4);
            else if (context.getEntity() != null) source = context.getEntity().createCommandSourceStack().withPermission(4);
            else if (context.getLevel() != null) {
                net.minecraft.server.level.ServerLevel serverLevel = context.getLevel();
                source = serverLevel.getServer().createCommandSourceStack().withLevel(serverLevel).withPermission(4);
            }
            else return List.of();

            return new net.minecraft.commands.arguments.selector.EntitySelectorParser(new com.mojang.brigadier.StringReader(finalSelector), true).parse().findEntities(source);

        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            System.err.println("[TargetSelector] 语法错误: " + finalSelector + " | 原因: " + e.getMessage());
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