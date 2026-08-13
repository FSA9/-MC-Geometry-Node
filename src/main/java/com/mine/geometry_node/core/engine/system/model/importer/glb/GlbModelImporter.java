package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.importer.*;

public final class GlbModelImporter implements ModelImporter {
    public static final String ID = "geometry_node:glb";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ModelDefinition importModel(ModelImportSource source, ModelImportSession session)
            throws ModelImportException {
        try {
            session.checkpoint("glb");
            GlbContainerReader.GlbContainer container = GlbContainerReader.read(source.readOnlyContent(), session);
            JsonObject root = GlbJson.parse(container.json());
            GlbDocument document = GlbDocument.parse(root, container.binary(), session);
            return new GlbModelAssembler(document, session).assemble(source.asset());
        } catch (ArithmeticException exception) {
            throw new ModelImportException(ModelImportFailure.simple(ModelImportErrorCode.INVALID_DATA,
                    "glb", "GLB size calculation overflowed"), exception);
        }
    }
}
