package com.mine.geometry_node.core.engine.behavior.blackboard;

import com.mine.geometry_node.core.engine.graph.scoped.OwnerScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateEntry;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateProvider;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateServerConfig;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateScope;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Team;

import java.util.Objects;
import java.util.Map;
import java.lang.ref.WeakReference;

/** Resolves declared scope references to stable server-side provider identities. */
public final class BehaviorScopedStateProviders {
    private BehaviorScopedStateProviders() {
    }

    public static void install(BehaviorBlackboard blackboard,
                               ServerLevel level, Entity owner) {
        Objects.requireNonNull(blackboard, "blackboard");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        ScopedStateStorage storage = ScopedStateStorage.get(level);
        int maxEntries = ScopedStateServerConfig.maxEntries(ScopedStateNamespace.PUBLIC);
        blackboard.installProvider(new OwnerScopedStateProvider(
                owner, ScopedStateNamespace.PUBLIC, maxEntries));
        blackboard.installProvider(storage.provider(
                ScopedStateNamespace.PUBLIC, ScopedStateScope.SHARED,
                "server", level, maxEntries));
        blackboard.installProvider(new CurrentGroupProvider(
                storage, level, owner, maxEntries));
        blackboard.installProvider(storage.provider(
                ScopedStateNamespace.PUBLIC, ScopedStateScope.WORLD,
                level.dimension().identifier().toString(), level, maxEntries));
    }

    private static final class CurrentGroupProvider implements ScopedStateProvider {
        private final ScopedStateStorage storage;
        private final WeakReference<ServerLevel> level;
        private final WeakReference<Entity> owner;
        private final int maxEntries;

        private CurrentGroupProvider(ScopedStateStorage storage, ServerLevel level,
                                     Entity owner, int maxEntries) {
            this.storage = storage;
            this.level = new WeakReference<>(level);
            this.owner = new WeakReference<>(owner);
            this.maxEntries = maxEntries;
        }

        @Override public ScopedStateScope scope() { return ScopedStateScope.GROUP; }
        @Override public String identity() {
            Team team = team();
            return team != null ? "scoreboard:" + team.getName() : "";
        }
        @Override public boolean available() { return team() != null && level.get() != null; }
        @Override public ScopedStateEntry get(String name) {
            return delegate().get(name);
        }
        @Override public ScopedStateEntry put(String name, Object value) {
            return delegate().put(name, value);
        }
        @Override public boolean remove(String name) {
            return delegate().remove(name);
        }
        @Override public boolean hasRecord(String name) { return delegate().hasRecord(name); }
        @Override public Map<String, ScopedStateEntry> entries() {
            return delegate().entries();
        }
        @Override public long revision() { return available() ? delegate().revision() : 0L; }
        @Override public int size() { return delegate().size(); }

        private ScopedStateProvider delegate() {
            Team team = team();
            if (team == null) {
                throw new ScopedStateAccessException(
                        "GROUP blackboard membership is unavailable");
            }
            ServerLevel currentLevel = level.get();
            if (currentLevel == null) {
                throw new ScopedStateAccessException(
                        "GROUP blackboard world is unavailable");
            }
            return storage.provider(ScopedStateNamespace.PUBLIC, ScopedStateScope.GROUP,
                    "scoreboard:" + team.getName(), currentLevel, maxEntries);
        }

        private Team team() {
            Entity entity = owner.get();
            return entity != null ? entity.getTeam() : null;
        }
    }
}
