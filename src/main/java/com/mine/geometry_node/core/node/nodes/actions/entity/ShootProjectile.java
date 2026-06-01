package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class ShootProjectile extends BaseNode {

    public static final String TYPE_ID = "shoot_projectile";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.shoot_projectile"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 输出生成的投掷物
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                // 输入：发射者与坐标
                .addRow(new PortRow(StandardPorts.SOURCE_ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.VECTOR, null, null))
                // 输入：运动参数
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(new Vec3(0, 0, 1)), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.SPEED.toInput(1.5f), null, UIHint.INPUT, null, null))
                // 【重构核心】使用 STRING 占位并隐藏针脚，通过 Options 渲染下拉框
                .addRow(new PortRow(
                        StandardPorts.STRING.toInput().hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, new String[]{"snowball", "arrow"})
                ))
                .addRow(new PortRow(StandardPorts.GRAVITY.toInput(true), null, UIHint.CHECKBOX, null, null))
                .addRow(new PortRow(StandardPorts.INVISIBLE.toInput(false), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Level level = context.getLevel();
        if (level == null || level.isClientSide()) return next(StandardPorts.FLOW_OUT.getId());

        Entity owner = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        Vec3 pos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 dir = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Float speed = getInput(context, StandardPorts.SPEED.getId(), Float.class);

        Boolean hasGravity = getInput(context, StandardPorts.GRAVITY.getId(), Boolean.class);
        Boolean isInvisible = getInput(context, StandardPorts.INVISIBLE.getId(), Boolean.class);
        String physicsType = getInput(context, StandardPorts.STRING.getId(), String.class);
        if (physicsType == null) physicsType = "snowball";

        if (pos == null) pos = Vec3.ZERO;
        if (dir == null) dir = new Vec3(0, 0, 1);
        if (speed == null) speed = 1.5f;
        if (hasGravity == null) hasGravity = true;
        if (isInvisible == null) isInvisible = false;

        Projectile projectile;

        if ("arrow".equals(physicsType)) {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setPos(pos.x, pos.y, pos.z);
            projectile = arrow;
        } else {
            Snowball snowball = new Snowball(EntityType.SNOWBALL, level);
            snowball.setPos(pos.x, pos.y, pos.z);

            if (isInvisible) {
                snowball.setItem(new ItemStack(Items.AIR));
            }
            projectile = snowball;
        }

        if (owner != null) {
            projectile.setOwner(owner);
        }

        // 设置重力与可见性
        projectile.setNoGravity(!hasGravity);
        if (isInvisible) {
            projectile.setInvisible(true);
        }

        projectile.shoot(dir.x, dir.y, dir.z, speed, 0.0f);

        level.addFreshEntity(projectile);
        context.setTempData("spawned_projectile", projectile);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getTempData("spawned_projectile");
        }
        return null;
    }
}