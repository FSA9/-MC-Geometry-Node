package com.mine.geometry_node.client.runtime.entity.appearance;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceVariants;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.entity.LivingEntity;

import java.util.Objects;

/** Package-private application; only ClientEntityAppearances' freshly constructed detached proxies enter here. */
final class AppearanceVariants {
    private AppearanceVariants() {}

    static void apply(LivingEntity proxy, EntityAppearanceSpec spec) {
        if (!proxy.level().isClientSide()) throw new IllegalArgumentException("Appearance proxy must be client-side");
        EntityAppearanceVariants.resolve(spec, proxy.level()).ifPresent(value -> apply(proxy, value));
    }

    private static <T> void apply(LivingEntity proxy, TypedDataComponent<T> value) {
        proxy.setComponent(value.type(), value.value());
        // setComponent returns void and silently ignores unsupported components. Fail closed on a bad binding.
        if (!Objects.equals(proxy.get(value.type()), value.value()))
            throw new IllegalArgumentException("Appearance proxy did not accept its registered variant component");
    }
}
