package com.mine.geometry_node.core.engine.system.entity.preview.protocol;

import com.mine.geometry_node.core.engine.system.entity.appearance.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Detached preview snapshot: the real safe template and optional replacement intent stay independent. */
public record EntityPreviewPayload(EntityTemplateValue template, EntityAppearanceSpec appearanceSpec) {
    public static final EntityPreviewPayload EMPTY = new EntityPreviewPayload(EntityTemplateValue.EMPTY, EntityAppearanceSpec.NONE);
    public EntityPreviewPayload {
        Objects.requireNonNull(template); Objects.requireNonNull(appearanceSpec);
        if (!template.isEmpty()) {
            if (template.entityTypeId().length() > EntityAppearanceLimits.MAX_TYPE_LENGTH
                    || Identifier.tryParse(template.entityTypeId()) == null)
                throw new IllegalArgumentException("Invalid preview template entity type");
            var safe = EntityAppearanceSanitizer.sanitize(template.data());
            EntityAppearanceNbt.encode(safe);
            template = new EntityTemplateValue(template.entityTypeId(), safe);
        }
    }
    /** A replacement can be previewed even when capture of the real entity is unsupported. */
    public boolean isEmpty() { return template.isEmpty() && appearanceSpec.isNone(); }
}
