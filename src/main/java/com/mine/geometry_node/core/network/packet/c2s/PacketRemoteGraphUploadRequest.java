package com.mine.geometry_node.core.network.packet.c2s;

import com.mine.geometry_node.core.engine.blueprint.execution.storage.RemoteGraphFileService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteGraphUploadRequest(
        int requestId,
        boolean preflightOnly,
        boolean overwrite,
        List<String> overwritePaths,
        List<RemoteGraphFileService.UploadFile> files
) implements CustomPacketPayload {
    public static final Type<PacketRemoteGraphUploadRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "remote_graph_upload_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteGraphUploadRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteGraphUploadRequest::new
    );

    public PacketRemoteGraphUploadRequest(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readBoolean(), readStrings(buf), readFiles(buf));
    }

    public PacketRemoteGraphUploadRequest(int requestId, boolean preflightOnly, boolean overwrite, List<RemoteGraphFileService.UploadFile> files) {
        this(requestId, preflightOnly, overwrite, List.of(), files);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(preflightOnly);
        buf.writeBoolean(overwrite);
        writeStrings(buf, overwritePaths);
        buf.writeInt(files.size());
        for (RemoteGraphFileService.UploadFile file : files) {
            buf.writeUtf(file.targetPath(), 32767);
            buf.writeUtf(file.jsonContent(), 262144);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(32767));
        }
        return values;
    }

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
        buf.writeInt(values.size());
        for (String value : values) {
            buf.writeUtf(value, 32767);
        }
    }

    private static List<RemoteGraphFileService.UploadFile> readFiles(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<RemoteGraphFileService.UploadFile> files = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            files.add(new RemoteGraphFileService.UploadFile(buf.readUtf(32767), buf.readUtf(262144)));
        }
        return files;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
