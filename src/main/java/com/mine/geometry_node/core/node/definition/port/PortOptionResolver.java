package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import net.minecraft.core.RegistryAccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Resolves SELECT options for both the editor widget and read-only Agent tools. */
public final class PortOptionResolver {
    public enum Source { NONE, STATIC, DYNAMIC }

    public record Option(String id, String label) {
        public Option {
            id = id == null ? "" : id;
            label = label == null ? id : label;
        }
    }

    public record Resolution(Source source, String registryId, boolean available, List<Option> options) {
        public Resolution {
            registryId = registryId == null ? "" : registryId;
            options = List.copyOf(options == null ? List.of() : options);
        }

        /** Stable token guarding SELECT writes against option-registry changes. */
        public String contextToken() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                update(digest, source.name());
                update(digest, registryId);
                update(digest, Boolean.toString(available));
                for (Option option : options) update(digest, option.id());
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

    private PortOptionResolver() {}

    public static Resolution resolve(PortRow row, RegistryAccess registryAccess,
                                     Function<String, String> translationResolver) {
        return resolve(row, registryAccess, translationResolver, ignored -> null);
    }

    /** Host catalogs can supply options which are not present in the local registry access. */
    public static Resolution resolve(PortRow row, RegistryAccess registryAccess,
                                     Function<String, String> translationResolver,
                                     Function<String, RegistryDataManager.DynamicOptions> hostOptions) {
        Resolution result = resolveOptions(row, registryAccess, translationResolver, hostOptions);
        if (row == null || row.hintParams() == null || result.source() == Source.NONE) return result;
        String emptyLabel = (String) row.hintParams().get(PortMetaKeys.EMPTY_OPTION_LABEL);
        if (emptyLabel == null || result.options().stream().anyMatch(option -> option.id().isEmpty())) return result;
        List<Option> options = new ArrayList<>();
        options.add(new Option("", translationResolver == null ? emptyLabel : translationResolver.apply(emptyLabel)));
        options.addAll(result.options());
        return new Resolution(result.source(), result.registryId(), result.available(), options);
    }

    private static Resolution resolveOptions(PortRow row, RegistryAccess registryAccess,
                                             Function<String, String> translationResolver,
                                             Function<String, RegistryDataManager.DynamicOptions> hostOptions) {
        if (row == null || row.uiHint() != UIHint.SELECT || row.hintParams() == null) {
            return new Resolution(Source.NONE, "", true, List.of());
        }
        Function<String, String> translate = translationResolver == null ? Function.identity() : translationResolver;
        String[] staticOptions = (String[]) row.hintParams().get(PortMetaKeys.OPTIONS);
        if (staticOptions != null && staticOptions.length > 0) {
            String[] labelKeys = (String[]) row.hintParams().get(PortMetaKeys.OPTION_LABELS);
            List<Option> options = new ArrayList<>(staticOptions.length);
            for (int index = 0; index < staticOptions.length; index++) {
                String id = staticOptions[index] == null ? "" : staticOptions[index];
                String label = id;
                if (labelKeys != null && index < labelKeys.length && labelKeys[index] != null
                        && !labelKeys[index].isBlank()) {
                    label = translate.apply(labelKeys[index]);
                }
                options.add(new Option(id, label));
            }
            return new Resolution(Source.STATIC, "", true, options);
        }

        String registryId = (String) row.hintParams().get(PortMetaKeys.DYNAMIC_REGISTRY_ID);
        if (registryId == null || registryId.isBlank()) {
            return new Resolution(Source.NONE, "", true, List.of());
        }
        RegistryDataManager.DynamicOptions live = hostOptions.apply(registryId);
        if (live == null) live = RegistryDataManager.getLiveDynamicOptions(registryId);
        if (live != null) {
            Map<String, String> labels = live.labels();
            List<Option> options = live.ids().stream()
                    .map(id -> new Option(id, labels.getOrDefault(id, id))).toList();
            return new Resolution(Source.DYNAMIC, registryId, live.available(), options);
        }
        List<String> ids = RegistryDataManager.getDynamicOptions(registryId, registryAccess);
        Map<String, String> labelKeys = RegistryDataManager.getDynamicOptionLabelKeys(registryId);
        List<Option> options = ids.stream().map(id -> {
            String labelKey = labelKeys.get(id);
            return new Option(id, labelKey == null || labelKey.isBlank() ? id : translate.apply(labelKey));
        }).toList();
        return new Resolution(Source.DYNAMIC, registryId, registryAccess != null || !options.isEmpty(), options);
    }
}
