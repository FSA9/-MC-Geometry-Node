package com.mine.geometry_node.client.ui.persistence.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mine.geometry_node.client.ui.persistence.PathUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    JsonObject loadRoot() throws IOException {
        File configFile = PathUtils.getConfigFile();
        if (!configFile.exists()) return null;

        String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IOException("config root must be an object");
        }
        return element.getAsJsonObject();
    }

    void save(AppConfig config) throws IOException {
        Path target = PathUtils.getConfigFile().toPath();
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(config), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void delete() throws IOException {
        Files.deleteIfExists(PathUtils.getConfigFile().toPath());
    }
}
