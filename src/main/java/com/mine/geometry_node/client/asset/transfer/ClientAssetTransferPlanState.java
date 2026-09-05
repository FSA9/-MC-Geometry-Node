package com.mine.geometry_node.client.asset.transfer;

import com.mine.geometry_node.client.network.request.ClientRequestTracker;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.AssetTransferPlanKind;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanResponse;

import java.util.List;
import java.util.function.Consumer;

public final class ClientAssetTransferPlanState {
    private static final ClientRequestTracker.Group REQUESTS =
            ClientRequestTracker.group("asset-transfer-plan");

    private ClientAssetTransferPlanState() {
    }

    public static int request(
            AssetTransferPlanKind kind,
            List<String> paths,
        Consumer<PacketAssetTransferPlanResponse> callback
    ) {
        int requestId = REQUESTS.register(PacketAssetTransferPlanResponse.class, callback);
        try {
            NetworkHandler.sendToServer(new PacketAssetTransferPlanRequest(requestId, kind, paths));
            return requestId;
        } catch (RuntimeException exception) {
            REQUESTS.cancel(requestId);
            throw exception;
        }
    }

    public static void handle(PacketAssetTransferPlanResponse response) {
        REQUESTS.complete(response.requestId(), response);
    }

    public static void cancel(int requestId) {
        REQUESTS.cancel(requestId);
    }

    public static void reset() {
        REQUESTS.reset();
    }
}
