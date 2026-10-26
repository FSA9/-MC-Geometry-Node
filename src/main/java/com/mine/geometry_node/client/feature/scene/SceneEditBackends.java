package com.mine.geometry_node.client.feature.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeInteractions;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneEdit;
import dev.architectury.networking.NetworkManager;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.BooleanSupplier;

/** Routes immutable edit values to their authority. All backends share the viewport's history. */
public final class SceneEditBackends {
    public static final String AUTHORED = "scene";
    public static final String NATIVE_ENTITIES = "native_entities";
    public enum Mutation { CREATE, UPDATE, DELETE }
    public record Backend(String id, Predicate<SceneObject> accepts, Consumer<PacketSceneEdit> send,
                          int syncIntervalTicks, BooleanSupplier subscribed, Set<Mutation> mutations,
                          java.util.function.BiPredicate<SceneObject,Mutation> supports) {
        public Backend {
            Objects.requireNonNull(id); Objects.requireNonNull(accepts); Objects.requireNonNull(send);
            Objects.requireNonNull(subscribed);
            Objects.requireNonNull(supports);
            mutations = Set.copyOf(mutations);
            if (id.isBlank() || id.equals(AUTHORED) || syncIntervalTicks < 1)
                throw new IllegalArgumentException("Invalid scene backend");
        }
        public Backend(String id, Predicate<SceneObject> accepts, Consumer<PacketSceneEdit> send,
                       int syncIntervalTicks, BooleanSupplier subscribed, Set<Mutation> mutations) {
            this(id,accepts,send,syncIntervalTicks,subscribed,mutations,(object,mutation)->true);
        }
    }
    private static final List<Backend> BACKENDS = new CopyOnWriteArrayList<>();
    private static volatile BooleanSupplier nativeSubscription = () -> false;
    static {
        register(new Backend(NATIVE_ENTITIES, object -> object.typeId().equals(SceneNativeInteractions.TYPE_ID)
                        || object.typeId().equals(SceneNativeEntities.TYPE_ID),
                packet -> NetworkManager.sendToServer(new com.mine.geometry_node.core.network.packet.viewport.PacketViewportNativeEntityEdit(packet)), 20,
                () -> nativeSubscription.getAsBoolean(),
                Set.of(Mutation.CREATE, Mutation.UPDATE, Mutation.DELETE),
                (object,mutation)->!object.typeId().equals(SceneNativeEntities.TYPE_ID)||mutation!=Mutation.UPDATE));
    }
    private SceneEditBackends() {}
    /** Application composition supplies host visibility and viewport demand together. */
    public static void configureNativeSubscription(BooleanSupplier subscribed) {
        nativeSubscription = Objects.requireNonNull(subscribed);
    }
    public static synchronized void register(Backend backend) {
        Objects.requireNonNull(backend);
        if (BACKENDS.stream().anyMatch(existing -> existing.id().equals(backend.id())))
            throw new IllegalArgumentException("Duplicate scene backend: " + backend.id());
        BACKENDS.add(backend);
    }
    public static List<Backend> all() { return List.copyOf(BACKENDS); }
    public static String owner(SceneObject object) {
        return authority(object).orElseThrow(() -> new IllegalArgumentException("Native type has no editing backend: " + object.typeId()));
    }
    private static java.util.Optional<String> authority(SceneObject object) {
        String owner = AUTHORED;
        for (Backend backend : BACKENDS) if (backend.accepts().test(object)) {
            if (!owner.equals(AUTHORED)) throw new IllegalArgumentException("Ambiguous scene backend");
            owner = backend.id();
        }
        if (owner.equals(AUTHORED) && SceneObjectTypes.require(object.typeId()).storage() == SceneObjectTypes.Storage.NATIVE)
            return java.util.Optional.empty();
        return java.util.Optional.of(owner);
    }
    public static boolean accepts(String backend, SceneObject object) { return owner(object).equals(backend); }
    public static boolean supports(SceneObject object, Mutation mutation) {
        var authority = authority(object);
        if (authority.isEmpty()) return false;
        String owner = authority.get();
        if (AUTHORED.equals(owner)) return true;
        return BACKENDS.stream().filter(backend -> backend.id().equals(owner))
                .anyMatch(backend -> backend.mutations().contains(mutation) && backend.supports().test(object,mutation));
    }
    public static void send(String backend, PacketSceneEdit packet) {
        if (AUTHORED.equals(backend)) { NetworkManager.sendToServer(packet); return; }
        BACKENDS.stream().filter(candidate -> candidate.id().equals(backend)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scene backend")).send().accept(packet);
    }
}
