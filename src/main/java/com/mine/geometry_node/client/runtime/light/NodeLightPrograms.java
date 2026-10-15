package com.mine.geometry_node.client.runtime.light;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;

/** Compilation and failure cleanup for light-owned full-screen programs. */
final class NodeLightPrograms {
    private NodeLightPrograms() {}

    record Linked(int id, boolean cached) {}

    static Linked link(String vertexSource, String fragmentSource) {
        var path = NodeLightProgramCache.path(vertexSource, fragmentSource);
        int cached = NodeLightProgramCache.load(path);
        if (cached != 0) return new Linked(cached, true);
        int vertex = 0, fragment = 0, program = 0;
        try {
            vertex = compile(GL20C.GL_VERTEX_SHADER, vertexSource);
            fragment = compile(GL20C.GL_FRAGMENT_SHADER, fragmentSource);
            program = GL20C.glCreateProgram();
            if (path != null)
                GL41C.glProgramParameteri(program, GL41C.GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11C.GL_TRUE);
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
            NodeLightProgramCache.save(path, program);
            return new Linked(program, false);
        } catch (RuntimeException error) {
            if (program != 0) GL20C.glDeleteProgram(program);
            throw error;
        } finally {
            if (vertex != 0) GL20C.glDeleteShader(vertex);
            if (fragment != 0) GL20C.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        try {
            GL20C.glShaderSource(shader, source);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(GL20C.glGetShaderInfoLog(shader));
            return shader;
        } catch (RuntimeException error) {
            GL20C.glDeleteShader(shader);
            throw error;
        }
    }
}
