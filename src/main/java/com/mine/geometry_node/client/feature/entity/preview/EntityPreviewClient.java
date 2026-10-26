package com.mine.geometry_node.client.feature.entity.preview;

import com.mine.geometry_node.client.platform.ClientServerIdentity;
import com.mine.geometry_node.core.cache.CacheRegistry;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewTarget;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.entitypreview.PacketEntityPreviewRequest;
import com.mine.geometry_node.core.network.packet.entitypreview.PacketEntityPreviewResponse;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Render/client-thread facade. Call resolve only from a visible native preview's render callback. */
public final class EntityPreviewClient {
    public static final EntityPreviewClient INSTANCE = new EntityPreviewClient();
    public enum State { LOADING, READY, UNAVAILABLE, ERROR }
    public record Result(State state, EntityTemplateValue template) {}

    private final EntityPreviewService<EntityTemplateValue> service;
    private final EntityPreviewPump pump;
    private boolean initialized;

    private EntityPreviewClient() {
        service = new EntityPreviewService<>(EntityTemplateValue.EMPTY, EntityTemplateValue::isEmpty,
                EntityPreviewClient::measure, System::nanoTime,
                (id, targets) -> NetworkHandler.sendToServer(new PacketEntityPreviewRequest(id, targets)),
                task -> Minecraft.getInstance().execute(() -> {
                    synchronizeConnection();
                    task.run();
                }), CacheRegistry.GLOBAL);
        pump = new EntityPreviewPump(task -> CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)
                .execute(() -> Minecraft.getInstance().execute(task)), this::pulse, service::hasWork);
    }

    /** Called during receiver bootstrap, before opening settings or rendering any previews. */
    public void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> pulse());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            requireClientThread();
            service.connection(null, null);
            pump.cancel();
        });
    }

    public Result resolve(UUID entityId, String dimension) {
        requireClientThread();
        synchronizeConnection();
        Minecraft minecraft = Minecraft.getInstance();
        if (entityId == null || minecraft.getConnection() == null || minecraft.level == null)
            return new Result(State.ERROR, EntityTemplateValue.EMPTY);
        String normalized = dimension == null || dimension.isBlank()
                ? minecraft.level.dimension().identifier().toString() : dimension;
        if (normalized.length() > 256) return new Result(State.ERROR, EntityTemplateValue.EMPTY);
        var result = service.resolve(new EntityPreviewTarget(entityId, normalized));
        pump.request();
        State state = switch (result.state()) {
            case LOADING -> State.LOADING;
            case READY -> State.READY;
            case UNAVAILABLE -> State.UNAVAILABLE;
            case ERROR -> State.ERROR;
        };
        return new Result(state, result.template());
    }

    /** Safe to capture on the network thread before queueing a response. */
    public long generation() { return service.generation(); }

    public void accept(PacketEntityPreviewResponse packet, long expectedGeneration) {
        requireClientThread();
        synchronizeConnection();
        service.accept(packet.requestId(), packet.entries().stream()
                .map(entry -> new EntityPreviewService.Entry<>(entry.target(), entry.template(), entry.message())).toList(),
                expectedGeneration);
        pump.request();
    }

    private void pulse() {
        requireClientThread();
        synchronizeConnection();
        service.tick();
        pump.request();
    }

    private void synchronizeConnection() {
        Minecraft minecraft = Minecraft.getInstance();
        Object connection = minecraft.getConnection();
        // A level transition within one connection does not invalidate snapshots of other dimensions.
        String namespace = null;
        if (connection != null) {
            try { namespace = ClientServerIdentity.current(); }
            catch (IllegalStateException unavailable) {
                // Settings cannot address a scope yet. Defer rather than inventing another namespace.
                connection = null;
            }
        }
        long before = service.generation();
        service.connection(connection, namespace);
        if (before != service.generation()) pump.cancel();
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Entity preview resolution requires the Minecraft client/render thread");
    }

    /** Bounded counting output measures the appearance without retaining a second serialized copy. */
    private static EntityPreviewService.Size measure(EntityTemplateValue template) {
        var output = new OutputStream() {
            long count;
            @Override public void write(int value) throws IOException { add(1); }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException { add(length); }
            private void add(int bytes) throws IOException {
                count += bytes;
                if (count > EntityPreviewService.MAX_APPEARANCE_BYTES) throw new IOException("Preview too large");
            }
        };
        try {
            var data = template.data();
            NbtIo.write(data, new DataOutputStream(output));
            return new EntityPreviewService.Size(
                    output.count + template.entityTypeId().getBytes(StandardCharsets.UTF_8).length,
                    data.sizeInBytes() + 2L * template.entityTypeId().length());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot measure preview appearance", failure);
        }
    }
}
