package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;

public class SpawnItemDisplayEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_item_display_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_item_display_entity"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.START_POS, "start_pos")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.STRING, "display_context")
                        .input(StandardPorts.TRANSLATION, "translation")
                        .input(StandardPorts.ROTATION, "rotation")
                        .input(StandardPorts.SIZE_3, "size_3")
                        .input(StandardPorts.TICK, "teleport_tick")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(), null, UIHint.SELECT, null,
                        Map.of(
                                PortMetaKeys.OPTIONS, new String[]{"none", "third_person_left_hand", "third_person_right_hand", "first_person_left_hand", "first_person_right_hand", "head", "gui", "ground", "fixed"},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.display.item_context.none",
                                        "geometry_node.display.item_context.third_person_left_hand",
                                        "geometry_node.display.item_context.third_person_right_hand",
                                        "geometry_node.display.item_context.first_person_left_hand",
                                        "geometry_node.display.item_context.first_person_right_hand",
                                        "geometry_node.display.item_context.head",
                                        "geometry_node.display.item_context.gui",
                                        "geometry_node.display.item_context.ground",
                                        "geometry_node.display.item_context.fixed"
                                }
                        )
                ))
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_3.toInput(new Vec3(1, 1, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(0)
                        .withDisplayName("geometry_node.port.tick.teleport"), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInputWithIndex(1, 0)
                        .withDisplayName("geometry_node.port.tick.interpolation"), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;

        ItemStack itemStack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        if (itemStack == null || itemStack.isEmpty()) itemStack = new ItemStack(Items.STONE);

        String displayContext = getInput(context, StandardPorts.STRING.getId(), String.class);
        if (displayContext == null) displayContext = "fixed";

        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Vec3 rotation = getInput(context, StandardPorts.ROTATION.getId(), Vec3.class);
        Vec3 scaleVec = getInput(context, StandardPorts.SIZE_3.getId(), Vec3.class);
        if (translation == null) translation = Vec3.ZERO;
        if (rotation == null) rotation = Vec3.ZERO;
        if (scaleVec == null) scaleVec = new Vec3(1, 1, 1);

        Integer tpDuration = getInput(context, StandardPorts.TICK.getId(), Integer.class);
        Integer interpDuration = getInput(context, StandardPorts.TICK.getIdWithIndex(1), Integer.class);

        Quaternionf leftRotation = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z)
        );

        Display.ItemDisplay displayEntity = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (displayEntity != null) {
            displayEntity.setPos(pos.x, pos.y, pos.z);

            CompoundTag nbt = EntityNbtCompat.saveWithoutId(displayEntity);

            Tag itemTag = ItemStack.OPTIONAL_CODEC
                    .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), itemStack)
                    .result()
                    .orElseGet(CompoundTag::new);
            nbt.put("item", itemTag);
            nbt.putString("item_display", displayContext);

            CompoundTag transTag = new CompoundTag();
            transTag.put("translation", createFloatList((float) translation.x, (float) translation.y, (float) translation.z));
            transTag.put("scale", createFloatList((float) scaleVec.x, (float) scaleVec.y, (float) scaleVec.z));
            transTag.put("left_rotation", createFloatList(leftRotation.x(), leftRotation.y(), leftRotation.z(), leftRotation.w()));
            transTag.put("right_rotation", createFloatList(0f, 0f, 0f, 1f));
            nbt.put("transformation", transTag);

            nbt.putInt("teleport_duration", tpDuration != null ? Math.max(0, tpDuration) : 0);
            nbt.putInt("interpolation_duration", interpDuration != null ? Math.max(0, interpDuration) : 0);
            nbt.putInt("start_interpolation", 0);

            EntityNbtCompat.load(displayEntity, nbt);
            level.addFreshEntity(displayEntity);
            context.setTempData("spawned_item_display", displayEntity);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData("spawned_item_display");
        }
        return null;
    }

    private ListTag createFloatList(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}
