package com.mine.geometry_node.client.feature.asset.preview.protocol;

import com.mine.geometry_node.client.platform.ClientThread;
import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferHashing;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewResponse;

import java.util.Objects;
import java.util.UUID;

/** Validates complete native preview responses; persistence is owned by the client preview service. */
public final class ClientAssetPreviewProtocol {
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("asset-preview", ClientThread::execute);

    private ClientAssetPreviewProtocol() {
    }

    public static UUID request(AssetPreviewRevision revision, Listener listener) {
        UUID id = UUID.randomUUID();
        Pending pending = new Pending(revision, listener);
        REQUESTS.register(id, PacketAssetPreviewResponse.class, pending::complete, () -> {
            sendCancellation(id);
            listener.failed(AssetPreviewResultCode.IO_FAILURE, "timeout");
        });
        try {
            NetworkHandler.sendToServer(new PacketAssetPreviewRequest(id, revision));
        } catch (RuntimeException exception) {
            REQUESTS.cancel(id);
            ClientThread.execute(
                    () -> listener.failed(AssetPreviewResultCode.IO_FAILURE, "send_failed"));
        }
        return id;
    }

    public static void cancel(UUID id) {
        if (id == null || !REQUESTS.cancel(id)) return;
        sendCancellation(id);
    }

    private static void sendCancellation(UUID id) {
        try {
            NetworkHandler.sendToServer(new PacketAssetPreviewCancel(id));
        } catch (RuntimeException ignored) {
            // Cancellation is best-effort when the connection is already closing.
        }
    }

    public static void reset() {
        REQUESTS.reset();
    }

    public static void handle(PacketAssetPreviewResponse packet) {
        REQUESTS.complete(packet.requestId(), packet);
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

        private void complete(PacketAssetPreviewResponse packet) {
            if (packet.code() != AssetPreviewResultCode.AVAILABLE) {
                listener.failed(packet.code(), packet.detail());
                return;
            }
            try {
                verifyAndComplete(packet.descriptor(), packet.content());
            } catch (RuntimeException exception) {
                listener.failed(AssetPreviewResultCode.IO_FAILURE, "verification_failed");
            }
        }

        private void verifyAndComplete(AssetPreviewDescriptor descriptor, byte[] content) {
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
