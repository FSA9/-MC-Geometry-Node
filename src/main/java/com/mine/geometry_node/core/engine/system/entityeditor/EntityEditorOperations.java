package com.mine.geometry_node.core.engine.system.entityeditor;

import java.util.Map;

/** Shared read/apply coordinator. The host supplies current permissions, loaded identity and a reversible mutation boundary. */
final class EntityEditorOperations {
    interface Access {
        boolean permitted();
        String target();
        Object incarnation();
        /** Must bound/validate response encoding before returning. */
        Map<String, Object> snapshot();
        /** Validate then commit, rolling back if mutation or response capture fails. Returns the authoritative snapshot. */
        Map<String, Object> apply(Map<String, Object> current, Map<String, Object> changes);
    }
    static final class Rejected extends RuntimeException {
        final String reason;
        Rejected(String reason) { this.reason = reason; }
    }
    private EntityEditorOperations() {}
    static EntityEditorSessions.Read execute(Access access, EntityEditorSessions session, String expectedRevision, Map<String, Object> replacement) {
        if (!access.permitted()) throw new Rejected("permission_denied");
        Map<String, Object> current = access.snapshot();
        if (!expectedRevision.isEmpty()) {
            EntityEditorSessions.Snapshot baseline = session.find(expectedRevision, access.target(), access.incarnation());
            if (baseline == null) throw new Rejected("conflict");
            Map<String, Object> changes = EntityEditorChanges.between(baseline.values(), replacement);
            if (EntityEditorChanges.conflicts(baseline.values(), current, changes)) throw new Rejected("conflict");
            if (!access.permitted()) throw new Rejected("permission_denied");
            current = access.apply(current, changes);
            session.consume(expectedRevision);
        }
        return session.remember(access.target(), access.incarnation(), current);
    }
}
