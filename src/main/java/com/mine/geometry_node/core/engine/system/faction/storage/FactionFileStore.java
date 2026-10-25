package com.mine.geometry_node.core.engine.system.faction.storage;

import com.mine.geometry_node.core.engine.system.faction.model.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** World-owned authored data; cache eviction never removes or rewrites this file. */
public final class FactionFileStore {
    private static final LevelResource ROOT = new LevelResource("data/geometry_node");
    private FactionFileStore() {}
    public static Path file(MinecraftServer server) { return server.getWorldPath(ROOT).resolve("factions.json").toAbsolutePath().normalize(); }
    public static FactionCatalog read(Path file) throws IOException {
        if (!Files.exists(file)) return FactionCatalog.EMPTY;
        if (Files.size(file) > FactionRules.MAX_DOCUMENT_BYTES) throw new IOException("Faction document is too large");
        try { return FactionJsonCodec.decode(Files.readString(file, StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid faction document; refusing to overwrite it", invalid); }
    }
    public static void write(Path file, FactionCatalog catalog) throws IOException {
        String encoded = FactionJsonCodec.encode(catalog); // Validate the complete projected document before touching disk.
        Path parent = file.toAbsolutePath().normalize().getParent();
        Files.createDirectories(parent);
        Path temporary = parent.resolve(".factions-write-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, encoded, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
