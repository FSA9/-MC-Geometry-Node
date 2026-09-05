package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.GeometryNode;
import com.mojang.serialization.Codec;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintCloseMode;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceLifecycleManager;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceScope;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * [世界级运行容器 - 组合版]
 * 继承 SavedData 实现持久化，内部委托 BlueprintProcessContainer 执行具体逻辑。
 */
public class LevelGraphAttachment extends SavedData {

    public static final SavedDataType<LevelGraphAttachment> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "level_processes"),
            ignored -> new LevelGraphAttachment(),
            LevelGraphAttachment::codec
    );

    @Nullable private ServerLevel level;
    private final BlueprintProcessContainer container = new BlueprintProcessContainer(
            this::setDirty, () -> {}, this::onProcessRemoved);

    public static LevelGraphAttachment get(ServerLevel level) {
        LevelGraphAttachment attachment = level.getDataStorage().computeIfAbsent(TYPE);
        attachment.level = level;
        return attachment;
    }

    public LevelGraphAttachment() {}

    /**
     * [心跳驱动] 全局图纸不绑实体，故 target 传 null
     */
    public void tick(ServerLevel level) {
        container.tick(level, null);
    }

    // --- API 委托层 ---

    public void addProcess(BlueprintProcess process) { container.addProcess(process); }
    public void removeProcess(String graphId) { container.removeProcess(graphId); }
    public void removeProcess(String graphId, BlueprintCloseMode closeMode) { container.removeProcess(graphId, closeMode); }
    public Collection<BlueprintProcess> getProcesses() { return container.getProcesses(); }
    public BlueprintProcess getProcess(String graphId) { return container.getProcess(graphId); }
    // --- 序列化层 ---

    private static Codec<LevelGraphAttachment> codec(ServerLevel level) {
        HolderLookup.Provider provider = level != null
                ? level.registryAccess()
                : HolderLookup.Provider.create(Stream.<HolderLookup.RegistryLookup<?>>empty());
        return CompoundTag.CODEC.xmap(
                tag -> load(tag, provider, level),
                attachment -> attachment.saveToTag(new CompoundTag(), provider)
        );
    }

    public static LevelGraphAttachment load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag, provider, null);
    }

    private static LevelGraphAttachment load(CompoundTag tag, HolderLookup.Provider provider,
                                             @Nullable ServerLevel level) {
        LevelGraphAttachment attachment = new LevelGraphAttachment();
        attachment.level = level;
        attachment.container.load(tag, provider);
        return attachment;
    }

    private CompoundTag saveToTag(CompoundTag tag, HolderLookup.Provider provider) {
        return container.save(tag, provider);
    }

    private void onProcessRemoved(BlueprintProcess process) {
        if (level != null) {
            GraphResourceLifecycleManager.INSTANCE.releaseBinding(level.getServer(),
                    new GraphResourceScope.LevelScope(level.dimension()),
                    GraphBindingKey.blueprint(process.getGraphId()));
        }
    }
}
