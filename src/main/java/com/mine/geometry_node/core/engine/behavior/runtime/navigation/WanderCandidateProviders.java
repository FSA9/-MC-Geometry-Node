package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry and deterministic fallback for behavior-tree Wander destinations. */
public final class WanderCandidateProviders {
    private static final int FALLBACK_SAMPLES = 10;
    private static final List<WanderCandidateProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();

    private WanderCandidateProviders() {
    }

    /**
     * Registers a specialized provider. The first registered provider that supports a mob wins;
     * returning {@code null} represents a failed proposal round and does not invoke a fallback.
     */
    public static void register(WanderCandidateProvider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    @Nullable
    public static Vec3 findCandidate(Mob mob, int horizontalRange, int verticalRange,
                                     Random random) {
        Objects.requireNonNull(mob, "mob");
        Objects.requireNonNull(random, "random");
        PathNavigation navigation = mob.getNavigation();
        WanderCandidateContext context = new WanderCandidateContext(mob, navigation,
                horizontalRange, verticalRange, random);
        for (WanderCandidateProvider provider : PROVIDERS) {
            if (provider.supports(mob, navigation)) {
                Vec3 candidate = provider.findCandidate(context);
                validateProviderCandidate(context, candidate);
                return candidate;
            }
        }
        return builtInCandidate(context);
    }

    @Nullable
    private static Vec3 builtInCandidate(WanderCandidateContext context) {
        PathNavigation navigation = context.navigation();
        if (navigation instanceof WaterBoundPathNavigation) {
            return bestCandidate(context, CandidateKind.WATER);
        }
        if (navigation instanceof FlyingPathNavigation) {
            return bestCandidate(context, CandidateKind.AIR);
        }
        if (navigation instanceof AmphibiousPathNavigation) {
            return bestCandidate(context, context.mob().isInWater()
                    ? CandidateKind.WATER : CandidateKind.GROUND);
        }
        return bestCandidate(context, navigation.canNavigateGround()
                ? CandidateKind.GROUND : CandidateKind.GENERIC);
    }

    @Nullable
    private static Vec3 bestCandidate(WanderCandidateContext context, CandidateKind kind) {
        Mob mob = context.mob();
        int horizontalRange = context.horizontalRange();
        boolean restrictToHome = mob instanceof PathfinderMob pathfinder
                && GoalUtils.mobRestricted(pathfinder, horizontalRange);
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestWeight = Double.NEGATIVE_INFINITY;
        for (int sample = 0; sample < FALLBACK_SAMPLES; sample++) {
            BlockPos candidate = normalizeCandidate(context, offsetCandidate(context), kind);
            if (candidate == null || candidate.equals(origin)
                    || mob.level().isOutsideBuildHeight(candidate.getY())
                    || !validForKind(context, candidate, kind)) {
                continue;
            }
            double weight = 0.0D;
            if (mob instanceof PathfinderMob pathfinder) {
                if (GoalUtils.isRestricted(restrictToHome, pathfinder, candidate)
                        || (kind == CandidateKind.GROUND
                        && GoalUtils.hasMalus(pathfinder, candidate))) {
                    continue;
                }
                weight = pathfinder.getWalkTargetValue(candidate);
            }
            if (weight > bestWeight) {
                bestWeight = weight;
                best = candidate;
            }
        }
        return best != null ? Vec3.atBottomCenterOf(best) : null;
    }

    private static void validateProviderCandidate(WanderCandidateContext context,
                                                  @Nullable Vec3 candidate) {
        if (candidate == null) return;
        if (!Double.isFinite(candidate.x) || !Double.isFinite(candidate.y)
                || !Double.isFinite(candidate.z)) {
            throw new IllegalArgumentException("Wander provider returned a non-finite candidate");
        }
        Mob mob = context.mob();
        double horizontalLimit = context.horizontalRange() + 1.0D;
        double verticalLimit = context.verticalRange() + 1.0D;
        if (Math.abs(candidate.x - mob.getX()) > horizontalLimit
                || Math.abs(candidate.z - mob.getZ()) > horizontalLimit
                || Math.abs(candidate.y - mob.getY()) > verticalLimit) {
            throw new IllegalArgumentException("Wander provider returned an out-of-range candidate");
        }
    }

    private static boolean validForKind(WanderCandidateContext context, BlockPos candidate,
                                        CandidateKind kind) {
        return switch (kind) {
            case GROUND -> context.navigation().isStableDestination(candidate);
            case WATER -> context.mob().level().getFluidState(candidate).is(FluidTags.WATER)
                    && context.mob().getPathfindingMalus(PathType.WATER) >= 0.0F
                    && hasWaterForMob(context.mob(), candidate)
                    && hasRoomForMob(context.mob(), candidate);
            case AIR -> context.mob().level().getFluidState(candidate).isEmpty()
                    && context.mob().getPathfindingMalus(context.navigation().getNodeEvaluator()
                    .getPathType(context.mob(), candidate)) >= 0.0F
                    && hasRoomForMob(context.mob(), candidate);
            case GENERIC -> hasRoomForMob(context.mob(), candidate);
        };
    }

    @Nullable
    private static BlockPos normalizeCandidate(WanderCandidateContext context,
                                               BlockPos candidate, CandidateKind kind) {
        if (kind != CandidateKind.GROUND) return candidate;
        LevelChunk chunk = context.mob().level().getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(candidate.getX()),
                SectionPos.blockToSectionCoord(candidate.getZ()));
        if (chunk == null) return null;
        int minY = Math.max(context.mob().level().getMinY(),
                context.mob().getBlockY() - context.verticalRange());
        int maxY = Math.min(context.mob().level().getMaxY(),
                context.mob().getBlockY() + context.verticalRange());
        int centerY = Math.max(minY, Math.min(maxY, candidate.getY()));
        for (int distance = 0; centerY - distance >= minY || centerY + distance <= maxY;
             distance++) {
            int lowerY = centerY - distance;
            if (lowerY >= minY) {
                BlockPos lower = new BlockPos(candidate.getX(), lowerY, candidate.getZ());
                if (usableGroundPosition(context, lower)) return lower;
            }
            int upperY = centerY + distance;
            if (distance > 0 && upperY <= maxY) {
                BlockPos upper = new BlockPos(candidate.getX(), upperY, candidate.getZ());
                if (usableGroundPosition(context, upper)) return upper;
            }
        }
        return null;
    }

    private static boolean usableGroundPosition(WanderCandidateContext context,
                                                BlockPos candidate) {
        if (!context.navigation().isStableDestination(candidate)
                || !hasRoomForMob(context.mob(), candidate)) {
            return false;
        }
        return !(context.mob() instanceof PathfinderMob pathfinder)
                || !GoalUtils.hasMalus(pathfinder, candidate);
    }

    private static boolean hasRoomForMob(Mob mob, BlockPos candidate) {
        return mob.level().noCollision(mob, boundingBoxAt(mob, candidate));
    }

    private static boolean hasWaterForMob(Mob mob, BlockPos candidate) {
        for (BlockPos occupied : BlockPos.betweenClosed(boundingBoxAt(mob, candidate))) {
            if (!mob.level().getFluidState(occupied).is(FluidTags.WATER)) return false;
        }
        return true;
    }

    private static AABB boundingBoxAt(Mob mob, BlockPos candidate) {
        Vec3 position = Vec3.atBottomCenterOf(candidate);
        return mob.getBoundingBox().move(
                position.x - mob.getX(), position.y - mob.getY(), position.z - mob.getZ());
    }

    private static BlockPos offsetCandidate(WanderCandidateContext context) {
        Mob mob = context.mob();
        int horizontalRange = context.horizontalRange();
        int verticalRange = context.verticalRange();
        Random random = context.random();
        double x = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
        int y = random.nextInt(verticalRange * 2 + 1) - verticalRange;
        double z = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
        if (mob.hasHome() && horizontalRange > 1) {
            BlockPos home = mob.getHomePosition();
            x += mob.getX() > home.getX()
                    ? -random.nextDouble() * horizontalRange / 2.0D
                    : random.nextDouble() * horizontalRange / 2.0D;
            z += mob.getZ() > home.getZ()
                    ? -random.nextDouble() * horizontalRange / 2.0D
                    : random.nextDouble() * horizontalRange / 2.0D;
        }
        return BlockPos.containing(mob.getX() + x, mob.getY() + y, mob.getZ() + z);
    }

    private enum CandidateKind {
        GROUND,
        WATER,
        AIR,
        GENERIC
    }
}
