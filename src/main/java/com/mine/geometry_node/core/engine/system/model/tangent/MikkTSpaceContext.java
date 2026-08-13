/*
 * Copyright (c) 2009-2021 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DAMAGES ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE.
 */
package com.mine.geometry_node.core.engine.system.model.tangent;

/** Callback surface used by the vendored jMonkeyEngine MikkTSpace translation. */
public interface MikkTSpaceContext {
    int getNumFaces();
    int getNumVerticesOfFace(int face);
    void getPosition(float[] output, int face, int vertex);
    void getNormal(float[] output, int face, int vertex);
    void getTexCoord(float[] output, int face, int vertex);
    void setTSpaceBasic(float[] tangent, float sign, int face, int vertex);
    void setTSpace(float[] tangent, float[] bitangent, float magnitudeS, float magnitudeT,
                   boolean orientationPreserving, int face, int vertex);
}
