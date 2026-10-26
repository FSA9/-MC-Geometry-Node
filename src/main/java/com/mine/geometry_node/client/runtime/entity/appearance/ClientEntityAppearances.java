package com.mine.geometry_node.client.runtime.entity.appearance;

import com.mine.geometry_node.core.cache.CacheDescriptor;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceAdapters;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceVariants;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

/** Client-thread detached visual resources. The source is never mutated and proxies never join a level. */
public final class ClientEntityAppearances {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ContextKey<EntityRenderer<?, ?>> MATCHED_RENDERER = new ContextKey<>(
            Identifier.fromNamespaceAndPath("geometry_node", "appearance_renderer"));
    private static final Set<Entity> INTERNAL = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Entity, Proxy> PREVIEWS = new IdentityHashMap<>();
    private static final AppearanceSessions<Entity, Level, EntityAppearanceSpec, Proxy> SESSIONS =
            new AppearanceSessions<>(ClientEntityAppearances::dispose, System::nanoTime,
                    Duration.ofSeconds(30).toNanos(), 1024);
    private static boolean initialized, reloadInstalled;
    private static volatile long generation;
    private static long variantProviderRevision = EntityAppearanceVariants.revision();
    private static CacheRegistry.Registration cacheRegistration;

    private static final EntityAppearanceAdapter DEFAULT = new EntityAppearanceAdapter() {
        @Override public boolean validateParameters(Map<String, String> parameters) {
            // Full syntax and current-world membership are checked by the common schema before this adapter.
            return Set.of("baby", EntityAppearanceVariants.PARAMETER).containsAll(parameters.keySet())
                    && (!parameters.containsKey("baby") || Set.of("true", "false").contains(parameters.get("baby")));
        }
        @Override public void apply(LivingEntity proxy, Map<String, String> parameters) {
            if (!parameters.containsKey("baby")) return;
            boolean baby = Boolean.parseBoolean(parameters.get("baby"));
            if (proxy instanceof AgeableMob mob) mob.setBaby(baby);
            else if (proxy instanceof Zombie zombie) zombie.setBaby(baby);
            else throw new IllegalArgumentException("This appearance target does not support baby");
        }
    };

    private record Proxy(LivingEntity entity, EntityAppearanceAdapter adapter) {}
    private ClientEntityAppearances() {}

    /** Idempotent game-bus lifecycle wiring. Reload wiring uses installLifecycle(modBus). */
    public static void init() {
        refreshVariantProviders();
        if (initialized) return;
        initialized = true;
        cacheRegistration = CacheRegistry.GLOBAL.register(new CacheDescriptor(
                "geometry_node:entity_appearance.proxies", "geometry_node.cache.entity_appearance_proxies",
                CacheDescriptor.Domain.CLIENT, CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.CONNECTION),
                new AppearanceCacheControl(ClientEntityAppearances::onClient, SESSIONS::size,
                        () -> generation, () -> {
                            generation++;
                            SESSIONS.clear();
                        }));
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel().isClientSide()) onClient(() -> release(event.getEntity()));
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof Level level && level.isClientSide())
                onClient(() -> {
                    generation++;
                    SESSIONS.clearContext(level);
                    for (Entity entity : PREVIEWS.keySet().stream().filter(entity -> entity.level() == level).toList())
                        releasePreviewEntity(entity);
                });
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> onClient(ClientEntityAppearances::clear));
        // Provider availability may depend on current tags. Drop successful holders and negative sessions alike.
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent.ClientPacketReceived event) -> onClient(ClientEntityAppearances::clear));
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> onClient(() -> {
            clear();
            cacheRegistration.close();
        }));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            SESSIONS.sweep(Entity::isRemoved);
            for (Entity entity : PREVIEWS.keySet().stream().filter(Entity::isRemoved).toList()) releasePreviewEntity(entity);
        });
    }

    public static void installLifecycle(IEventBus modBus) {
        init();
        if (reloadInstalled) return;
        reloadInstalled = true;
        modBus.addListener((AddClientReloadListenersEvent event) -> event.addListener(
                Identifier.fromNamespaceAndPath("geometry_node", "entity_appearances"),
                (ResourceManagerReloadListener) manager -> onClient(ClientEntityAppearances::clear)));
    }

    /** Null means no replacement: the caller must use its original renderer/extraction path. */
    public static @Nullable ResolvedAppearance resolve(Entity source, float partialTicks) {
        return INTERNAL.contains(source) ? null : resolve(source, EntityAppearances.get(source), partialTicks);
    }

    /** Explicit preview intent always takes precedence; this overload never reads an attachment. */
    public static @Nullable ResolvedAppearance resolve(Entity source, EntityAppearanceSpec spec, float partialTicks) {
        init();
        if (INTERNAL.contains(source) || !SESSIONS.enter(source)) return null;
        try {
            Proxy proxy = acquire(source, spec, partialTicks);
            if (proxy == null) return null;
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            EntityRenderer<Entity, EntityRenderState> renderer = renderer(proxy.entity());
            // Complete extraction includes renderer-specific fields, finalize/shadows and NeoForge extensions.
            EntityRenderState state = renderer.createRenderState(proxy.entity(), partialTicks);
            if (source instanceof LivingEntity living && state instanceof LivingEntityRenderState target) {
                // These values are authoritative even for a custom walking scale/interpolation implementation.
                target.walkAnimationPos = living.isAlive() && !living.isPassenger()
                        ? living.walkAnimation.position(partialTicks) : 0;
                target.walkAnimationSpeed = living.isAlive() && !living.isPassenger()
                        ? living.walkAnimation.speed(partialTicks) : 0;
                target.wornHeadAnimationPos = living.getVehicle() instanceof LivingEntity vehicle
                        ? vehicle.walkAnimation.position(partialTicks) : target.walkAnimationPos;
            }
            proxy.adapter().finish(source, proxy.entity(), state, partialTicks);
            // Source renderer evaluates crosshair, name visibility, team and NeoForge identity events.
            // Extract only; source shadow finalization and extension callbacks are not needed here.
            var sourceRenderer = dispatcher.getRenderer(source);
            EntityRenderState identity = sourceRenderer.createRenderState();
            extractIdentity(sourceRenderer, source, identity, partialTicks);
            state.nameTag = identity.nameTag;
            state.scoreText = identity.scoreText;
            state.outlineColor = identity.outlineColor;
            state.isDiscrete = identity.isDiscrete;
            // Use target attachments for visual placement, falling back to the source for unusual renderers.
            if (state.nameTagAttachment == null) state.nameTagAttachment = proxy.entity().getAttachments()
                    .getNullable(EntityAttachment.NAME_TAG, 0, proxy.entity().getYRot(partialTicks));
            if (state.nameTagAttachment == null) state.nameTagAttachment = identity.nameTagAttachment;
            if (state instanceof LivingEntityRenderState livingState && source instanceof LivingEntity)
                livingState.isInvisibleToPlayer = source.isInvisibleTo(Minecraft.getInstance().player);
            AABB visualBounds = bounds(proxy, renderer);
            // Bind for all callers, including explicit preview extraction that bypasses dispatcher.extractEntity.
            state.setRenderData(MATCHED_RENDERER, renderer);
            return new ResolvedAppearance(renderer, state, visualBounds, source, proxy.entity());
        } catch (RuntimeException failure) {
            fail(source, failure);
            return null;
        } finally {
            SESSIONS.exit(source);
        }
    }

    /** Returns the private target instance; never add, tick, interact with, modify or retain it. */
    public static Entity visualEntity(Entity source) {
        if (INTERNAL.contains(source) || !SESSIONS.enter(source)) return source;
        try {
            init();
            Proxy proxy = acquire(source, EntityAppearances.get(source), 1.0f);
            return proxy == null ? source : proxy.entity();
        } catch (RuntimeException failure) {
            fail(source, failure);
            return source;
        } finally { SESSIONS.exit(source); }
    }

    /** Current-tick world coordinates, independent of the real source's physical bounds. */
    public static AABB visualBounds(Entity source) {
        return visualBounds(source, INTERNAL.contains(source) ? EntityAppearanceSpec.NONE : EntityAppearances.get(source));
    }

    public static AABB visualBounds(Entity source, EntityAppearanceSpec spec) {
        if (INTERNAL.contains(source) || !SESSIONS.enter(source)) return source.getBoundingBox();
        try {
            init();
            Proxy proxy = acquire(source, spec, 1.0f);
            return proxy == null ? source.getBoundingBox() : bounds(proxy, renderer(proxy.entity()));
        } catch (RuntimeException failure) {
            fail(source, failure);
            return source.getBoundingBox();
        } finally { SESSIONS.exit(source); }
    }

    /** The target renderer decides distance/culling; no mutable render state is allocated here. */
    public static boolean shouldRender(Entity source, Frustum frustum, double x, double y, double z) {
        return shouldRender(source, INTERNAL.contains(source) ? EntityAppearanceSpec.NONE : EntityAppearances.get(source),
                frustum, x, y, z);
    }

    public static boolean shouldRender(Entity source, EntityAppearanceSpec spec, Frustum frustum, double x, double y, double z) {
        if (!INTERNAL.contains(source) && SESSIONS.enter(source)) {
            try {
                init();
                Proxy proxy = acquire(source, spec, 1.0f);
                if (proxy != null) return renderer(proxy.entity()).shouldRender(proxy.entity(), frustum, x, y, z);
            } catch (RuntimeException failure) { fail(source, failure); }
            finally { SESSIONS.exit(source); }
        }
        return renderer(source).shouldRender(source, frustum, x, y, z);
    }

    /**
     * A detached preview for a payload whose real template is unavailable. The view owns/discards it.
     * Parameters are applied once; render directly, without applying the spec a second time.
     */
    public static @Nullable Entity createPreviewEntity(Level level, EntityAppearanceSpec spec) {
        init();
        if (level == null || !level.isClientSide() || spec == null || spec.isNone()) return null;
        try {
            Proxy proxy = create(level, spec);
            if (proxy == null) return null;
            PREVIEWS.put(proxy.entity(), proxy);
            return proxy.entity();
        } catch (RuntimeException failure) {
            AppearanceFailures.rethrowFatal(failure);
            LOGGER.debug("Unable to create detached appearance preview {}", spec.targetTypeId(), failure);
            return null;
        }
    }

    public static void release(Entity source) { SESSIONS.remove(source); }
    /** Matched renderer bound to this one extraction, including direct/explicit preview resolutions. */
    public static @Nullable EntityRenderer<?, ?> rendererFor(EntityRenderState state) {
        return state.getRenderData(MATCHED_RENDERER);
    }
    /** Allows detached-preview owners to rebuild after runtime invalidation/reload. */
    public static long generation() { refreshVariantProviders(); return generation; }
    /** Release an owned detached preview (including a real-template preview), never a live world entity. */
    public static void releasePreviewEntity(Entity entity) {
        if (entity == null) return;
        SESSIONS.remove(entity);
        Proxy proxy = PREVIEWS.remove(entity);
        if (proxy != null) dispose(proxy);
        else if (!entity.isRemoved()) entity.discard();
    }

    /** Call on the client thread. Reload/connection callbacks marshal here before mutation. */
    public static void clear() {
        generation++;
        SESSIONS.clear();
        var previous = java.util.List.copyOf(PREVIEWS.values());
        PREVIEWS.clear();
        previous.forEach(ClientEntityAppearances::dispose);
    }

    private static void refreshVariantProviders() {
        long revision = EntityAppearanceVariants.revision();
        if (variantProviderRevision != revision) {
            variantProviderRevision = revision;
            clear();
        }
    }

    private static @Nullable Proxy acquire(Entity source, EntityAppearanceSpec spec, float partialTicks) {
        if (spec == null || spec.isNone() || source.isRemoved() || !source.level().isClientSide()
                || source instanceof Player || source instanceof Interaction || source.getType() == EntityType.MANNEQUIN) {
            release(source);
            return null;
        }
        Proxy proxy = SESSIONS.acquire(source, source.level(), spec, () -> create(source.level(), spec));
        if (proxy == null) return null;
        synchronize(source, proxy.entity());
        proxy.adapter().synchronize(source, proxy.entity(), partialTicks);
        inert(proxy.entity());
        return proxy;
    }

    private static @Nullable Proxy create(Level level, EntityAppearanceSpec spec) {
        EntityAppearanceAdapters.validate(spec, level);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spec.targetTypeId())).orElse(null);
        if (type == null || !EntityAppearanceCatalog.candidate(type) || !type.isEnabled(level.enabledFeatures())) return null;
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return null;
        EntityAppearanceAdapter adapter = EntityAppearanceCatalog.adapter(spec.targetTypeId());
        if (adapter == null) adapter = DEFAULT;
        Proxy proxy = entity instanceof LivingEntity living ? new Proxy(living, adapter) : null;
        try {
            if (proxy == null || entity instanceof Player || entity.getType() != type)
                throw new IllegalArgumentException("Appearance factory did not produce the target living type");
            INTERNAL.add(entity);
            inert(entity);
            EntityAppearanceAdapters.validateParameters(spec, entity);
            if (!adapter.validateParameters(spec.parameters())) throw new IllegalArgumentException("Missing client parameter adapter");
            adapter.apply(proxy.entity(), spec.parameters());
            AppearanceVariants.apply(proxy.entity(), spec);
            inert(entity);
            return proxy;
        } catch (RuntimeException failure) {
            AppearanceFailures.rethrowFatal(failure);
            if (proxy != null) dispose(proxy);
            else entity.discard();
            throw failure;
        }
    }

    private static void inert(Entity entity) {
        entity.noPhysics = true;
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setCanPickUpLoot(false);
        }
    }

    private static void synchronize(Entity source, LivingEntity target) {
        target.setPos(source.getX(), source.getY(), source.getZ());
        target.xOld = source.xOld; target.yOld = source.yOld; target.zOld = source.zOld;
        target.xo = source.xo; target.yo = source.yo; target.zo = source.zo;
        target.setYRot(source.getYRot()); target.setXRot(source.getXRot());
        target.yRotO = source.yRotO; target.xRotO = source.xRotO;
        target.tickCount = source.tickCount;
        target.setPose(source.getPose());
        target.setOnGround(source.onGround());
        target.setDeltaMovement(source.getDeltaMovement());
        target.setShiftKeyDown(source.isShiftKeyDown());
        target.setSprinting(source.isSprinting());
        target.setSwimming(source.isSwimming());
        target.setInvisible(source.isInvisible());
        target.setTicksFrozen(source.getTicksFrozen());
        target.setSharedFlagOnFire(source.isOnFire());
        target.setCustomName(source.getCustomName());
        target.setCustomNameVisible(source.isCustomNameVisible());
        if (source instanceof LivingEntity living) {
            target.yBodyRot = living.yBodyRot; target.yBodyRotO = living.yBodyRotO;
            target.yHeadRot = living.yHeadRot; target.yHeadRotO = living.yHeadRotO;
            target.attackAnim = living.attackAnim; target.oAttackAnim = living.oAttackAnim;
            target.swinging = living.swinging; target.swingingArm = living.swingingArm; target.swingTime = living.swingTime;
            target.hurtTime = living.hurtTime; target.deathTime = living.deathTime;
            if (living.getSleepingPos().isPresent()) target.setSleepingPos(living.getSleepingPos().get());
            else target.clearSleepingPos();
            if (target instanceof Mob mob) mob.setLeftHanded(living.getMainArm() == HumanoidArm.LEFT);
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack item = living.getItemBySlot(slot);
                if (!ItemStack.matches(item, target.getItemBySlot(slot))) target.setItemSlot(slot, item.copy());
            }
            copyWalk(living.walkAnimation, target.walkAnimation);
        } else {
            target.yBodyRot = target.yBodyRotO = source.getYRot();
            target.yHeadRot = target.yHeadRotO = source.getYRot();
        }
    }

    /** Reconstructs public walk inputs without a tick or dependence on extraction count. */
    private static void copyWalk(WalkAnimationState source, WalkAnimationState target) {
        float speed = source.speed();
        float delta = source.position(1.0f) - source.position(0.0f);
        float scale = speed == 0 ? 1.0f : delta / speed;
        if (!Float.isFinite(scale) || scale == 0) scale = 1.0f;
        target.stop();
        target.update(source.position() / scale - speed, 1.0f, scale);
        target.setSpeed(source.speed(0));
        target.update(speed, 1.0f, scale);
    }

    private static AABB bounds(Proxy proxy, EntityRenderer<Entity, EntityRenderState> renderer) {
        AABB box = proxy.adapter().visualBounds(proxy.entity(), renderer);
        if (box == null || box.hasNaN() || !Double.isFinite(box.minX) || !Double.isFinite(box.minY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.maxZ)) throw new IllegalArgumentException("Invalid appearance visual bounds");
        return box;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRenderer<Entity, EntityRenderState> renderer(Entity entity) {
        return (EntityRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void extractIdentity(EntityRenderer renderer, Entity source, EntityRenderState state, float partialTicks) {
        renderer.extractRenderState(source, state, partialTicks);
    }

    private static void fail(Entity source, RuntimeException failure) {
        AppearanceFailures.rethrowFatal(failure);
        SESSIONS.fail(source);
        LOGGER.debug("Unable to resolve appearance for {}", source.getUUID(), failure);
    }

    private static void dispose(Proxy proxy) {
        try { proxy.adapter().release(proxy.entity()); }
        catch (RuntimeException failure) {
            AppearanceFailures.rethrowFatal(failure);
            LOGGER.warn("Unable to release appearance adapter", failure);
        }
        finally {
            try { proxy.entity().discard(); }
            catch (RuntimeException failure) {
                AppearanceFailures.rethrowFatal(failure);
                LOGGER.warn("Unable to discard detached appearance", failure);
            }
            finally { INTERNAL.remove(proxy.entity()); }
        }
    }

    private static void onClient(Runnable action) { Minecraft.getInstance().execute(action); }
}
