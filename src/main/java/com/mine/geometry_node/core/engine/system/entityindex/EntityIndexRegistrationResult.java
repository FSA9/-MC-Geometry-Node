package com.mine.geometry_node.core.engine.system.entityindex;

import java.util.Objects;
import java.util.Optional;

/** Revision is the current UUID registration generation, or zero after unregistration. */
public record EntityIndexRegistrationResult(long revision, boolean changed, Optional<EntityIndexEntry> entry) {
    public EntityIndexRegistrationResult { Objects.requireNonNull(entry); }
}
