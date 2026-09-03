package com.mine.geometry_node.core.node.nodes.actions.projectile;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.projectile.ProjectileCollisionPolicy;
import com.mine.geometry_node.core.engine.blueprint.projectile.ProjectileImpactController;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.mixin.AbstractArrowAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class ShootProjectile extends BaseNode {

    public static final String TYPE_ID = "shoot_projectile";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.shoot_projectile"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("collision_policy")
                        .text("impact_priority")
                        .input(StandardPorts.PROJECTILE, "projectile")
                        .input(StandardPorts.SOURCE_ENTITY, "source_entity")
                        .input(StandardPorts.IGNORE_AIR_RESISTANCE, "ignore_air_resistance")
                        .output(StandardPorts.PROJECTILE, "projectile_output")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.PROJECTILE.toOutput(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PROJECTILE.toInput(), null, UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.SOURCE_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.START_POS.toInput(), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.VECTOR.toInput(new Vec3(0, 0, 1)), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.SPEED.toInput(1.5f), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.COLLISION_POLICY
                                .toInput(ProjectileCollisionPolicy.VANILLA.id())
                                .hiddenPin(), UIHint.SELECT, null, Map.of(
                                PortMetaKeys.OPTIONS, ProjectileCollisionPolicy.OPTION_IDS,
                                PortMetaKeys.OPTION_LABELS,
                                ProjectileCollisionPolicy.OPTION_LABEL_KEYS
                        ))
                .addPassthroughInput(StandardPorts.GRAVITY.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.IGNORE_AIR_RESISTANCE.toInput(false), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.INVISIBLE.toInput(false), UIHint.CHECKBOX)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.PROJECTILE.getId(), null);
        Level level = context.getLevel();
        if (level == null || level.isClientSide()) return next(StandardPorts.FLOW_OUT.getId());

        Projectile projectile = getInput(context, StandardPorts.PROJECTILE.getId(), Projectile.class);
        if (projectile == null || projectile.isRemoved() || projectile.level() != level) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        Entity owner = getInput(context, StandardPorts.SOURCE_ENTITY.getId(), Entity.class);
        Vec3 pos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 direction = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Float speed = getInput(context, StandardPorts.SPEED.getId(), Float.class);
        Boolean hasGravity = getInput(context, StandardPorts.GRAVITY.getId(), Boolean.class);
        Boolean ignoreAirResistance = getInput(context, StandardPorts.IGNORE_AIR_RESISTANCE.getId(), Boolean.class);
        Boolean invisible = getInput(context, StandardPorts.INVISIBLE.getId(), Boolean.class);
        String policyId = getInput(context, StandardPorts.COLLISION_POLICY.getId(), String.class);

        if (pos == null) pos = projectile.position();
        if (direction == null) direction = new Vec3(0, 0, 1);
        if (speed == null) speed = 1.5f;
        if (hasGravity == null) hasGravity = true;
        if (ignoreAirResistance == null) ignoreAirResistance = false;
        if (invisible == null) invisible = false;

        if (owner != null) {
            projectile.setOwner(owner);
        }
        var projectileControl = projectile.getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT);
        projectileControl.setCollisionPolicy(ProjectileCollisionPolicy.parse(policyId));
        projectileControl.setRetained(false);
        projectileControl.setIgnoreAirResistance(ignoreAirResistance);
        projectile.syncData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT);
        if (projectile instanceof AbstractArrow arrow) {
            ((AbstractArrowAccessor) arrow).geometryNode$setInGround(false);
        }

        projectile.setPos(pos.x, pos.y, pos.z);
        projectile.setNoGravity(!hasGravity);
        projectile.setInvisible(invisible);
        projectile.shoot(direction.x, direction.y, direction.z, speed, 0.0f);
        ProjectileImpactController.markRelaunched(projectile);
        context.setNodeResult(StandardPorts.PROJECTILE.getId(), projectile);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.PROJECTILE.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.PROJECTILE.getId());
        }
        return null;
    }
}
