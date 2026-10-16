package com.mine.geometry_node.client.runtime.light.shader;

import java.util.LinkedHashMap;
import java.util.Map;

/** Last completed Iris ProgramBuilder build per name, bounded across pack switches. */
public final class NodeLightShaderBuilds {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, Map<String, Object>> LAST = new LinkedHashMap<>();
    private static final ThreadLocal<Build> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<String> CACHE_FAILURE = new ThreadLocal<>();

    private record Build(String name, long start, long patched, boolean nodeLights, int characters, String cache) {}

    private NodeLightShaderBuilds() {}

    public static void begin(String name) {
        CACHE_FAILURE.remove();
        CURRENT.set(new Build(name, System.nanoTime(), 0, false, 0, "NOT_APPLICABLE"));
    }

    public static void patched(String fragment) {
        Build build = CURRENT.get();
        if (build != null) CURRENT.set(new Build(build.name(), build.start(), System.nanoTime(),
                fragment != null && fragment.contains("uniform int nodeLightCount;"),
                fragment == null ? 0 : fragment.length(), build.cache()));
    }

    public static void cache(String status) {
        Build build = CURRENT.get();
        if (build != null) CURRENT.set(new Build(build.name(), build.start(), build.patched(),
                build.nodeLights(), build.characters(), status));
    }

    public static void cacheFailure(String failure) { CACHE_FAILURE.set(failure); }

    public static void finish() {
        Build build = CURRENT.get();
        CURRENT.remove();
        String failure = CACHE_FAILURE.get();
        CACHE_FAILURE.remove();
        if (failure == null) failure = "";
        if (build == null || build.patched() == 0) return;
        double patchMs = (build.patched() - build.start()) / 1_000_000.0;
        double compileLinkMs = (System.nanoTime() - build.patched()) / 1_000_000.0;
        synchronized (LAST) {
            LAST.remove(build.name());
            LAST.put(build.name(), Map.of("patchMs", patchMs, "compileLinkMs", compileLinkMs,
                    "nodeLights", build.nodeLights(), "fragmentCharacters", build.characters(), "binaryCache", build.cache(),
                    "cacheFailure", failure));
            while (LAST.size() > 64) LAST.remove(LAST.keySet().iterator().next());
        }
        if (patchMs + compileLinkMs >= 250 || build.cache().equals("HIT")) LOGGER.info(
                "Shader build {}: patch={} ms, compile/link/load={} ms, nodeLights={}, fragmentChars={}, binaryCache={}, cacheFailure={}",
                build.name(), Math.round(patchMs), Math.round(compileLinkMs), build.nodeLights(), build.characters(), build.cache(), failure);
    }

    public static Map<String, Object> report() {
        synchronized (LAST) {
            return Map.of("scope", "Last completed ProgramBuilder.begin per name; excludes Iris AST and terrain pipeline builds",
                    "programs", new LinkedHashMap<>(LAST));
        }
    }
}
