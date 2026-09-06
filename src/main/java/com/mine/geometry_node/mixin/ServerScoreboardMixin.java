package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.graph.scoped.storage.ScopedStateStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Couples persistent GROUP scoped-state lifetime to its scoreboard team identity. */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "onTeamRemoved", at = @At("TAIL"))
    private void geometryNode$removeGroupScopedState(PlayerTeam team, CallbackInfo callback) {
        ScopedStateStorage.removeGroup(server, team.getName());
    }
}
