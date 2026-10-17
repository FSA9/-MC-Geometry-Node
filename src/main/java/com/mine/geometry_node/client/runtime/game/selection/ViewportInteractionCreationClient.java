package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.viewport.PacketViewportInteractionCreate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** UI factories only enqueue immutable intent; world/network access stays on the client thread. */
public final class ViewportInteractionCreationClient {
    private ViewportInteractionCreationClient() {}
    public static void create(long generation, Vec3 position) {
        var snapshot = ClientSceneService.INSTANCE.snapshot();
        if (snapshot.worldGeneration() != generation || !snapshot.canEdit()) return;
        var packet = new PacketViewportInteractionCreate(UUID.randomUUID(), Identifier.parse(snapshot.dimension()), position);
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (ClientSceneService.INSTANCE.snapshot().worldGeneration() != generation || mc.level == null
                    || mc.getConnection() == null || !GameViewportInput.isEditor(mc.screen)
                    || !mc.level.dimension().identifier().equals(packet.dimension())) return;
            NetworkHandler.sendToServer(packet);
        });
    }
}
