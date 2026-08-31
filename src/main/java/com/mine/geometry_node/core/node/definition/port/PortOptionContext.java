package com.mine.geometry_node.core.node.definition.port;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable token guarding SELECT writes against option-registry changes. */
public final class PortOptionContext {
    private PortOptionContext() {}

    public static String token(PortOptionResolver.Resolution resolution) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, resolution.source().name());
            update(digest, resolution.registryId());
            update(digest, Boolean.toString(resolution.available()));
            for (PortOptionResolver.Option option : resolution.options()) update(digest, option.id());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
