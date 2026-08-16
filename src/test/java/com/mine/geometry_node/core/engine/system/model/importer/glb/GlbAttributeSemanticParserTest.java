package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAttributeSemantic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlbAttributeSemanticParserTest {
    @Test void parsesGeneralGltfAttributeSemanticsWithoutAliases() {
        assertEquals(ModelAttributeSemantic.POSITION, GlbAttributeSemanticParser.parse("POSITION"));
        assertEquals(ModelAttributeSemantic.NORMAL, GlbAttributeSemanticParser.parse("NORMAL"));
        assertEquals(ModelAttributeSemantic.TANGENT, GlbAttributeSemanticParser.parse("TANGENT"));
        assertEquals(ModelAttributeSemantic.TEXCOORD_0, GlbAttributeSemanticParser.parse("TEXCOORD_0"));
        assertEquals(ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD, 12),
                GlbAttributeSemanticParser.parse("TEXCOORD_12"));
        assertNull(GlbAttributeSemanticParser.parse("TEXCOORD_00"));
        assertNull(GlbAttributeSemanticParser.parse("CUSTOM_0"));
    }
}
