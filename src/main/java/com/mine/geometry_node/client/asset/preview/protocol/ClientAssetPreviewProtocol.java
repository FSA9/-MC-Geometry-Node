package com.mine.geometry_node.client.asset.preview.protocol;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewResponse;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validates complete native preview responses; persistence is owned by the client preview service. */
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
        if (id == null || PENDING.remove(id) == null) return;
        try {
            NetworkHandler.sendToServer(new PacketAssetPreviewCancel(id));
        } catch (RuntimeException ignored) {
            // Cancellation is best-effort when the connection is already closing.
        }
    }

    public static void reset() {
        PENDING.clear();
    }

    public static void handle(PacketAssetPreviewResponse packet) {
        Pending pending = PENDING.remove(packet.requestId());
        if (pending == null) return;
        if (packet.code() != AssetPreviewResultCode.AVAILABLE) {
            pending.listener.failed(packet.code(), packet.detail());
            return;
        }
        try {
            pending.complete(packet.descriptor(), packet.content());
        } catch (Exception exception) {
            pending.listener.failed(AssetPreviewResultCode.IO_FAILURE, "verification_failed");
        }
    }

    public interface Listener {
        void completed(AssetPreviewDescriptor descriptor, byte[] encoded);

        void failed(AssetPreviewResultCode code, String detail);
    }

    private static final class Pending {
        private final AssetPreviewRevision expectedRevision;
        private final Listener listener;

        private Pending(AssetPreviewRevision expectedRevision, Listener listener) {
            this.expectedRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        private void complete(AssetPreviewDescriptor descriptor, byte[] content) {
            if (descriptor == null || !expectedRevision.equals(descriptor.revision())
                    || content.length != descriptor.encodedBytes()) {
                throw new IllegalStateException("Invalid native preview response");
            }
            String sha256 = AssetTransferHashing.toHex(AssetTransferHashing.newSha256().digest(content));
            if (!sha256.equals(descriptor.sha256())) throw new IllegalStateException("Preview SHA-256 mismatch");
            listener.completed(descriptor, content);
        }
    }
}
