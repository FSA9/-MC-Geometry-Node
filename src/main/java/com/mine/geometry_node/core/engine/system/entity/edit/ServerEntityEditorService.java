package com.mine.geometry_node.core.engine.system.entity.edit;

import com.mine.geometry_node.core.engine.metadata.*;
import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraits;
import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraitOverrides;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.security.WorldEditPermissions;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataDataValues;
import com.mine.geometry_node.core.metadata.*;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entityeditor.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Async protocol boundary; all entity, metadata, repository and inventory work runs on the server task loop.
 * No chunk acquisition, generic entity NBT writes, asset mutations or client-origin stack writes.
 */
public final class ServerEntityEditorService {
    private static final Map<ServerGamePacketListenerImpl, EntityEditorSessions> SESSIONS = new IdentityHashMap<>();
    private static boolean initialized;
    private ServerEntityEditorService() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player.connection);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            SESSIONS.keySet().removeIf(connection -> connection.getPlayer().level().getServer() == event.getServer());
        });
    }

    /** Network registration queues this call; direct integrations may call it from either thread. */
    public static void receive(ServerPlayer original, PacketEntityEditorRequest request) {
        MinecraftServer server = original.level().getServer();
        if (!server.isSameThread()) {
            server.schedule(server.wrapRunnable(() -> receive(original, request))); return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(original.getUUID());
        if (player == null || player.connection != original.connection || player.hasDisconnected() || !server.isRunning()) return;
        PacketEntityEditorResult result;
        try {
            if (!WorldEditPermissions.canManageWorld(player)) throw new Rejected("permission_denied");
            if (!player.isAlive() || player.isSpectator()) throw new Rejected("unavailable");
            EntityEditorSessions session = SESSIONS.computeIfAbsent(player.connection, ignored -> new EntityEditorSessions());
            if (!session.acquire()) throw new Rejected("rate_limited");
            result = process(player, request, session);
        } catch (Rejected rejected) { result = PacketEntityEditorResult.error(request.requestId(), rejected.reason); }
        catch (EntityEditorOperations.Rejected rejected) { result = PacketEntityEditorResult.error(request.requestId(), rejected.reason); }
        catch (IllegalArgumentException invalid) { result = PacketEntityEditorResult.error(request.requestId(), "invalid"); }
        catch (RuntimeException failure) {
            System.getLogger(ServerEntityEditorService.class.getName()).log(System.Logger.Level.WARNING, "Entity editor request failed", failure);
            result = PacketEntityEditorResult.error(request.requestId(), "failed");
        }
        NetworkHandler.sendToPlayer(player, result);
    }

    private static PacketEntityEditorResult process(ServerPlayer player, PacketEntityEditorRequest request, EntityEditorSessions session) {
        MinecraftServer server = player.level().getServer();
        if (request.edit()) {
            try { GraphMetadataDataValues.encode(request.replacement(), server.registryAccess(), GraphMetadataDataValues.MAX_WRITE_BYTES); }
            catch (IllegalArgumentException tooLarge) { throw new Rejected("too_large"); }
        }
        Identifier dimension = Identifier.tryParse(request.dimension());
        if (dimension == null) throw new Rejected("invalid_dimension");
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) throw new Rejected("dimension_unavailable");
        Entity entity = level.getEntity(request.entity()); // Loaded index only; never asks for a chunk.
        requireLoaded(level, entity);
        // Player equipment aliases inventory/hotbar. It needs a distinct multi-owner inventory transaction.
        if (entity instanceof Player) throw new Rejected("unsupported_entity");
        String target = level.dimension().identifier() + "/" + entity.getUUID();
        EntityEditorAdapter adapter = EntityEditorAdapters.resolve(entity);
        EntityEditorSessions.Read read = EntityEditorOperations.execute(new EntityEditorOperations.Access() {
            public boolean permitted() { return WorldEditPermissions.canManageWorld(player); }
            public String target() { return target; }
            public Object incarnation() { return entity; }
            public Map<String, Object> snapshot() {
                Map<String, Object> values = ServerEntityEditorService.snapshot(player, entity, adapter);
                ensureReadable(values, server); return values;
            }
            public Map<String, Object> apply(Map<String, Object> current, Map<String, Object> changes) {
                return ServerEntityEditorService.apply(player, level, entity, adapter, current, changes);
            }
        }, session, request.expectedRevision(), request.replacement());
        return new PacketEntityEditorResult(request.requestId(), read.values(), read.revision(), "");
    }

    private static void requireLoaded(ServerLevel level, Entity entity) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.level() != level
                || level.getEntity(entity.getUUID()) != entity) throw new Rejected("entity_unavailable");
    }

    private static Map<String, Object> snapshot(ServerPlayer player, Entity entity, EntityEditorAdapter adapter) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("attributes", adapter.attributes(entity));
        List<Map<String, Object>> attributeFields = adapter.attributeFields(entity);
        // An old extension adapter has no schema. Absence preserves the client's legacy field fallback.
        if (!attributeFields.isEmpty()) values.put("attributeFields", attributeFields);
        values.put("registryType", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        values.put("dimension", entity.level().dimension().identifier().toString());
        values.put("position", Map.of("x", entity.getX(), "y", entity.getY(), "z", entity.getZ()));
        values.put("biologicalTypes", EntityTypeTraits.overrides(entity).values());
        values.put("biologicalTypeDefaults", EntityTypeTraits.defaults(entity));
        values.put("factions", com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.snapshot(entity));
        values.put("tags", EntityMetadataAdapter.tags(entity).values());
        values.put("data", EntityMetadataAdapter.data(entity).values());
        List<EquipmentSlot> slots = equipmentSlots(entity, adapter);
        Map<String, Object> equipment = new LinkedHashMap<>();
        if (entity instanceof LivingEntity living)
            for (EquipmentSlot slot : slots) equipment.put(slot.getName(), living.getItemBySlot(slot).copy());
        values.put("equipment", equipment);
        List<ItemStack> inventory = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) inventory.add(player.getInventory().getItem(i).copy());
        values.put("inventory", inventory);
        values.put("equipmentCreative", player.hasInfiniteMaterials());
        values.put("equipmentDrops", List.<ItemStack>of());
        values.put("graphs", EntityEditorGraphs.values(EntityEditorGraphs.bindings(entity)));
        values.put("availableGraphs", EntityEditorGraphs.available(entity, adapter));
        values.put("living", entity instanceof LivingEntity); values.put("equipmentSupported", !slots.isEmpty());
        addPreview(entity, values);
        return GraphMetadataDataValues.snapshot(values);
    }

    private static void addPreview(Entity entity, Map<String, Object> values) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null || !id.getNamespace().equals("minecraft") || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || entity instanceof Interaction || id.getPath().equals("mannequin")
                || !entity.getType().canSerialize() || !entity.getType().canSummon()) return;
        try { values.put("preview", new EntityTemplateValue(id.toString(), EntityAppearanceCapture.capture(entity))); }
        catch (RuntimeException unavailable) { /* Optional appearance support must not disable the editor. */ }
    }

    private static List<EquipmentSlot> equipmentSlots(Entity entity, EntityEditorAdapter adapter) {
        List<EquipmentSlot> slots = List.copyOf(adapter.equipmentSlots(entity));
        if (!(entity instanceof LivingEntity) && !slots.isEmpty() || new HashSet<>(slots).size() != slots.size()
                || !EntityEditorAdapters.EQUIPMENT.containsAll(slots)) throw new IllegalArgumentException("Invalid equipment capability");
        // A stable protocol order avoids adapter enumeration order becoming part of the revision.
        return EntityEditorAdapters.EQUIPMENT.stream().filter(slots::contains).toList();
    }

    private static Map<String, Object> apply(ServerPlayer player, ServerLevel level, Entity entity, EntityEditorAdapter adapter,
                              Map<String, Object> current, Map<String, Object> changes) {
        Map<String, Object> attributes = changes.containsKey("attributes") ? EntityEditorChanges.dictionary(changes.get("attributes")) : Map.of();
        adapter.validateAttributes(entity, attributes);
        EntityTypeTraitOverrides biologicalTypes = changes.containsKey("biologicalTypes")
                ? new EntityTypeTraitOverrides(EntityTypeTraits.validate(EntityEditorChanges.dictionary(changes.get("biologicalTypes")))) : null;
        var factions = changes.containsKey("factions")
                ? com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.validate(entity, changes.get("factions")) : null;
        List<String> tags = null;
        if (changes.containsKey("tags")) {
            if (!(changes.get("tags") instanceof List<?> raw) || raw.stream().anyMatch(value -> !(value instanceof String)))
                throw new IllegalArgumentException("Invalid tags");
            tags = TagSet.normalize(raw.stream().map(String.class::cast).toList(), TagPolicy.EXACT);
        }
        Map<String, Object> data = changes.containsKey("data")
                ? DataDictionary.snapshot(EntityEditorChanges.dictionary(changes.get("data")), GraphMetadataValuePolicy.scoped(level.registryAccess())) : null;
        Set<GraphBindingKey> graphs = changes.containsKey("graphs") ? EntityEditorGraphs.validate(entity, adapter, changes.get("graphs")) : null;
        EntityEditorItemTransfer<ItemStack> transfer = changes.containsKey("equipment") ? transfer(player, entity, adapter, changes) : null;

        // Check the projected response size before any writes, including the full components in all slots.
        Map<String, Object> projected = new LinkedHashMap<>(current);
        projected.putAll(changes);
        Map<String, Object> allAttributes = new LinkedHashMap<>(EntityEditorChanges.dictionary(current.get("attributes")));
        allAttributes.putAll(attributes); projected.put("attributes", allAttributes);
        ensureReadable(projected, level.getServer());
        requireLoaded(level, entity);
        if (!WorldEditPermissions.canManageWorld(player)) throw new Rejected("permission_denied");
        if (transfer != null) requireInventoryAuthority(player, current);

        Deque<Runnable> undo = new ArrayDeque<>();
        Map<String, Object> committed;
        try {
            if (!attributes.isEmpty()) {
                Map<String, Object> previous = new LinkedHashMap<>();
                Map<String, Object> old = EntityEditorChanges.dictionary(current.get("attributes"));
                attributes.keySet().forEach(key -> previous.put(key, old.get(key)));
                EntityEditorAttributes.CAPACITY_STATES.forEach((maximum, state) -> {
                    if (attributes.containsKey(maximum) && old.containsKey(state)) previous.put(state, old.get(state));
                });
                var previousName = entity.getCustomName();
                undo.push(() -> { adapter.applyAttributes(entity, previous); if (attributes.containsKey("name")) entity.setCustomName(previousName); });
                adapter.applyAttributes(entity, attributes);
            }
            if (biologicalTypes != null) {
                var before = EntityTypeTraits.overrides(entity);
                undo.push(() -> EntityTypeTraits.replace(entity, before));
                EntityTypeTraits.replace(entity, biologicalTypes);
            }
            if (factions != null) {
                var before = com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.read(entity);
                undo.push(() -> com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.replace(entity, before));
                com.mine.geometry_node.core.engine.system.faction.service.EntityFactions.replace(entity, factions);
            }
            if (tags != null) {
                var access = EntityMetadataAdapter.tags(entity); List<String> before = access.values();
                undo.push(() -> access.replace(before)); access.replace(tags);
                if (!access.values().equals(tags)) throw new IllegalArgumentException("Tags rejected");
            }
            if (data != null) {
                var access = EntityMetadataAdapter.data(entity); Map<String, Object> before = access.values();
                undo.push(() -> access.replace(before)); access.replace(data);
                if (!GraphMetadataDataValues.equivalent(access.values(), data)) throw new IllegalArgumentException("Data capacity rejected");
            }
            if (transfer != null) {
                requireInventoryAuthority(player, current);
                undo.push(transfer::rollback); transfer.apply();
            }
            if (graphs != null) {
                Set<GraphBindingKey> before = EntityEditorGraphs.bindings(entity);
                undo.push(() -> EntityEditorGraphs.replace(entity, before)); EntityEditorGraphs.replace(entity, graphs);
            }
            // Response capture/encoding is part of the rollback boundary, so a successful write cannot return an encoding error.
            committed = snapshot(player, entity, adapter);
            ensureReadable(committed, level.getServer());
        } catch (RuntimeException failure) {
            while (!undo.isEmpty()) {
                try { undo.pop().run(); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        if (transfer != null) {
            transfer.notifyCommitted();
            try {
                player.getInventory().setChanged();
                // Explicit updates also cover inventory slots outside the currently selected hotbar slot.
                for (int i = 0; i < 36; i++) player.connection.send(player.getInventory().createInventoryUpdatePacket(i));
                player.inventoryMenu.broadcastChanges();
            } catch (RuntimeException notification) {
                System.getLogger(ServerEntityEditorService.class.getName()).log(System.Logger.Level.WARNING, "Inventory commit notification failed", notification);
            }
        }
        // Directory snapshots are updated only after the edit has passed the rollback boundary.
        try {
            com.mine.geometry_node.core.engine.system.entity.index.ServerEntityIndexService.INSTANCE.refresh(level.getServer(), entity.getUUID());
        } catch (RuntimeException failure) {
            System.getLogger(ServerEntityEditorService.class.getName()).log(System.Logger.Level.WARNING,
                    "Committed entity edit could not refresh its directory snapshot", failure);
        }
        return committed;
    }

    private static EntityEditorItemTransfer<ItemStack> transfer(ServerPlayer player, Entity entity, EntityEditorAdapter adapter, Map<String, Object> changes) {
        List<EquipmentSlot> equipmentSlots = equipmentSlots(entity, adapter);
        if (equipmentSlots.isEmpty() || !(entity instanceof LivingEntity living)) throw new IllegalArgumentException("No equipment support");
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) throw new Rejected("inventory_busy");
        Map<String, Object> equipment = EntityEditorChanges.dictionary(changes.get("equipment"));
        Set<String> expected = new LinkedHashSet<>(); equipmentSlots.forEach(slot -> expected.add(slot.getName()));
        if (!equipment.keySet().equals(expected) || !(changes.get("inventory") instanceof List<?> inventory) || inventory.size() != 36)
            throw new IllegalArgumentException("Invalid equipment/inventory shape");
        Object rawDrops = changes.getOrDefault("equipmentDrops", List.of());
        if (!(rawDrops instanceof List<?> drops) || drops.size() > ConservedItemStacks.MAX_DROPS)
            throw new IllegalArgumentException("Invalid equipment drops");
        List<ItemStack> requestedDrops = drops.stream().map(ServerEntityEditorService::stack).toList();
        List<EntityEditorItemTransfer.Slot<ItemStack>> slots = new ArrayList<>(); List<ItemStack> requested = new ArrayList<>();
        for (EquipmentSlot slot : equipmentSlots) {
            requested.add(stack(equipment.get(slot.getName())));
            slots.add(new EntityEditorItemTransfer.Slot<>() {
                public ItemStack get() { return living.getItemBySlot(slot); }
                public boolean accepts(ItemStack stack) { return adapter.acceptsEquipment(entity, slot, stack); }
                public void set(ItemStack stack) { living.setItemSlot(slot, stack, true); }
                public void committed(ItemStack before, ItemStack after) { living.onEquipItem(slot, before, after); }
            });
        }
        for (int i = 0; i < 36; i++) {
            int index = i; requested.add(stack(inventory.get(i)));
            slots.add(new EntityEditorItemTransfer.Slot<>() {
                public ItemStack get() { return player.getInventory().getItem(index); }
                public boolean accepts(ItemStack stack) { return stack.isEmpty() || stack.getCount() <= player.getInventory().getMaxStackSize(stack); }
                public void set(ItemStack stack) { player.getInventory().setItem(index, stack); }
            });
        }
        return EntityEditorItemTransfer.prepare(slots, requested, requestedDrops, player.hasInfiniteMaterials(), stack -> {
            // Do not use Player.drop: toss hooks may mutate inventories or replace entities outside our undo batch.
            ServerLevel dropLevel = player.level();
            ItemEntity drop = new ItemEntity(dropLevel, player.getX(), player.getEyeY() - 0.3, player.getZ(), stack.copy());
            drop.setNeverPickUp();
            drop.setThrower(player);
            return new EntityEditorItemTransfer.Drop() {
                public void spawn() {
                    if (!dropLevel.addFreshEntity(drop))
                        throw new IllegalStateException("World rejected equipment drop");
                    validate();
                }
                public void validate() {
                    if (drop.isRemoved() || dropLevel.getEntity(drop.getUUID()) != drop || !ItemStack.matches(drop.getItem(), stack))
                        throw new IllegalStateException("World changed equipment drop");
                }
                public void remove() {
                    // Empty first: even a failing mod removal callback must not leave collectible copies behind.
                    drop.setItem(ItemStack.EMPTY);
                    try { drop.discard(); }
                    catch (RuntimeException notification) {
                        System.getLogger(ServerEntityEditorService.class.getName()).log(System.Logger.Level.WARNING,
                                "Empty equipment drop removal notification failed", notification);
                    }
                }
                public void committed() { drop.setDefaultPickUpDelay(); }
            };
        });
    }
    private static void requireInventoryAuthority(ServerPlayer player, Map<String, Object> current) {
        if (!Objects.equals(current.get("equipmentCreative"), player.hasInfiniteMaterials())) throw new Rejected("conflict");
        if (!player.isAlive() || player.isSpectator()) throw new Rejected("unavailable");
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) throw new Rejected("inventory_busy");
    }
    private static ItemStack stack(Object value) {
        if (!(value instanceof ItemStack stack)) throw new IllegalArgumentException("Expected full ItemStack");
        return stack;
    }
    private static void ensureReadable(Map<String, Object> values, MinecraftServer server) {
        try { GraphMetadataDataValues.encode(values, server.registryAccess(), GraphMetadataDataValues.MAX_READ_BYTES); }
        catch (IllegalArgumentException tooLarge) { throw new Rejected("too_large"); }
    }
    private static final class Rejected extends RuntimeException {
        private final String reason;
        private Rejected(String reason) { this.reason = reason; }
    }
}
