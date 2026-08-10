package com.mine.geometry_node.client.asset.preview.protocol;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.c2s.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewAccepted;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewChunk;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewComplete;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewResult;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates and assembles bounded preview packets; persistence is owned by the client preview service. */
public final class ClientAssetPreviewProtocol {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private ClientAssetPreviewProtocol() {
    }

    public static UUID request(AssetPreviewRevision revision, Listener listener) {
        UUID id = UUID.randomUUID();
        PENDING.put(id, new Pending(revision, listener));
        NetworkHandler.sendToServer(new PacketAssetPreviewRequest(id, revision));
        return id;
    }

    public static void cancel(UUID id) {
        if (id != null && PENDING.remove(id) != null) {
            NetworkHandler.sendToServer(new PacketAssetPreviewCancel(id));
        }
    }

    public static void reset() {
        PENDING.clear();
    }

    public static void handle(PacketAssetPreviewAccepted packet) {
        Pending pending = PENDING.get(packet.requestId());
        if (pending != null && !pending.accept(packet.descriptor())) {
            fail(packet.requestId(), AssetPreviewResultCode.INVALID_REQUEST, "invalid_accept");
        }
    }

    public static void handle(PacketAssetPreviewChunk packet) {
        Pending pending = PENDING.get(packet.requestId());
        if (pending != null && !pending.chunk(packet)) {
            fail(packet.requestId(), AssetPreviewResultCode.INVALID_REQUEST, "invalid_sequence");
        }
    }

    public static void handle(PacketAssetPreviewComplete packet) {
        Pending pending = PENDING.remove(packet.requestId());
        if (pending == null) return;
        try {
            pending.complete();
        } catch (Exception exception) {
            pending.listener.failed(AssetPreviewResultCode.IO_FAILURE, "verification_failed");
        }
    }

    public static void handle(PacketAssetPreviewResult packet) {
        Pending pending = PENDING.remove(packet.requestId());
        if (pending != null) pending.listener.failed(packet.code(), packet.detail());
    }

    private static void fail(UUID id, AssetPreviewResultCode code, String detail) {
        Pending pending = PENDING.remove(id);
        if (pending == null) return;
        NetworkHandler.sendToServer(new PacketAssetPreviewCancel(id));
        pending.listener.failed(code, detail);
    }

    public interface Listener {
        void completed(AssetPreviewDescriptor descriptor, byte[] encoded);

        void failed(AssetPreviewResultCode code, String detail);
    }

    private static final class Pending {
        private final AssetPreviewRevision expectedRevision;
        private final Listener listener;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private AssetPreviewDescriptor descriptor;
        private int sequence;

        private Pending(AssetPreviewRevision expectedRevision, Listener listener) {
            this.expectedRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        private boolean accept(AssetPreviewDescriptor value) {
            if (descriptor != null || value == null || !expectedRevision.equals(value.revision())) return false;
            descriptor = value;
            return true;
        }

        private boolean chunk(PacketAssetPreviewChunk packet) {
            byte[] content = packet.content();
            if (descriptor == null || packet.sequence() != sequence || packet.offset() != output.size()
                    || output.size() + content.length > descriptor.encodedBytes()) {
                return false;
            }
            output.writeBytes(content);
            sequence++;
            return true;
        }

        private void complete() {
            byte[] bytes = output.toByteArray();
            if (descriptor == null || bytes.length != descriptor.encodedBytes()) {
                throw new IllegalStateException("Incomplete preview response");
            }
            String sha256 = AssetTransferHashing.toHex(AssetTransferHashing.newSha256().digest(bytes));
            if (!sha256.equals(descriptor.sha256())) throw new IllegalStateException("Preview SHA-256 mismatch");
            listener.completed(descriptor, bytes);
        }
    }
}
