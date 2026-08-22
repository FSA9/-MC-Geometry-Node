package com.mine.geometry_node.core.node.port;

import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
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
    }

    private PortOptionResolver() {}

    public static Resolution resolve(PortRow row, RegistryAccess registryAccess,
                                     Function<String, String> translationResolver) {
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
        List<String> ids = RegistryDataManager.getDynamicOptions(registryId, registryAccess);
        Map<String, String> labelKeys = RegistryDataManager.getDynamicOptionLabelKeys(registryId);
        List<Option> options = ids.stream().map(id -> {
            String labelKey = labelKeys.get(id);
            return new Option(id, labelKey == null || labelKey.isBlank() ? id : translate.apply(labelKey));
        }).toList();
        return new Resolution(Source.DYNAMIC, registryId, registryAccess != null || !options.isEmpty(), options);
    }
}
