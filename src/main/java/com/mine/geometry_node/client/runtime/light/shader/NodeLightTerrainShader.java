package com.mine.geometry_node.client.runtime.light.shader;

import com.mine.geometry_node.client.model.render.bvh.ModelSundialHeldLight;

/** Native material receivers. Only known terrain signatures are patched; shaderpack programs are separate. */
public final class NodeLightTerrainShader {
    private static final String VARYING = "nodeTerrainPosition";
    private NodeLightTerrainShader() {}

    public static String patchSodium(String path, String source) {
        if(source==null||source.contains(VARYING))return source;
        if(path.equals("blocks/block_layer_opaque.vsh")) {
            String anchor="vec3 position = _vert_position + translation;";
            if(!source.contains(anchor)||!source.contains("v_Color = _vert_color * texture(u_LightTex"))return source;
            return beforeMain(source,"out vec3 nodeTerrainPosition;\nout vec4 nodeTerrainTint;\n")
                    .replace(anchor,anchor+"\nnodeTerrainPosition=position;nodeTerrainTint=_vert_color;");
        }
        if(path.equals("blocks/block_layer_opaque.fsh")) {
            String anchor="color *= v_Color;";
            String fog="fragColor = _linearFog(color,";
            if(!source.contains(anchor)||!source.contains(fog))return source;
            return beforeMain(source,fragmentDeclarations())
                    .replace(anchor,"vec3 nodeTerrainAlbedo=color.rgb*nodeTerrainTint.rgb;\n"+anchor)
                    .replace(fog,"color.rgb=nodeTerrainShade(color.rgb,nodeTerrainAlbedo,color.a);\n"+fog);
        }
        return source;
    }

    /** Called after the vanilla import preprocessor; identity on models and unrelated shaders. */
    public static String patch(String source) {
        if(source==null||source.contains(VARYING))return source;
        String vertex="vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;";
        if(source.contains(vertex)&&source.contains("vertexColor = Color * sample_lightmap(Sampler2, UV2);"))
            return beforeMain(source,"out vec3 nodeTerrainPosition;\nout vec4 nodeTerrainTint;\n")
                    .replace(vertex,vertex+"\nnodeTerrainPosition=pos;nodeTerrainTint=Color;");
        String sample="vec4 color = (UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize)) * vertexColor;";
        if(source.contains(sample)&&source.contains("color = mix(FogColor * vec4(1, 1, 1, color.a), color, ChunkVisibility);"))
            return beforeMain(source,fragmentDeclarations()).replace(sample,
                    "vec4 nodeTerrainTexel=(UseRgss == 1 ? sampleRGSS(Sampler0, texCoord0, 1.0f / TextureSize) : sampleNearest(Sampler0, texCoord0, 1.0f / TextureSize));\n"
                    +"vec4 color=nodeTerrainTexel*vertexColor;\ncolor.rgb=nodeTerrainShade(color.rgb,nodeTerrainTexel.rgb*nodeTerrainTint.rgb,color.a);");
        return source;
    }

    private static String fragmentDeclarations() {
        return "in vec3 nodeTerrainPosition;\nin vec4 nodeTerrainTint;\nuniform int nodeTerrainReceiver;\nuniform int nodeTerrainTranslucent;\n"
                +ModelSundialHeldLight.declarations()+"\n"+NodeLightShader.nativeDeclarations()+"\n"
                +"vec3 nodeTerrainShade(vec3 color,vec3 albedo,float opacity) {\n"
                +"if(nodeTerrainReceiver==0 || nodeLightCount==0)return color;\n"
                +"vec3 n=cross(dFdx(nodeTerrainPosition),dFdy(nodeTerrainPosition));\n"
                +"float len=dot(n,n);if(len<1e-12)return color;n*=inversesqrt(len);\n"
                +"vec3 linearAlbedo=pow(max(albedo,vec3(0.0)),vec3(2.2));\n"
                // NativeBinding always sets nodeLightResolved=0; native terrain has no surface atlas.
                // A shared material loop keeps one shadow traversal call site for both receiver types.
                +"vec3 light=nodeMaterialSurface(nodeTerrainPosition,n,n,linearAlbedo,opacity,0.2,0.04,nodeTerrainTranslucent!=0);\n"
                +"vec3 linearColor=pow(max(color,vec3(0.0)),vec3(2.2));\n"
                +"return pow(linearColor+light,vec3(1.0/2.2));\n}\n";
    }
    private static String beforeMain(String source,String declarations) {
        return source.replace("void main()",declarations+"\nvoid main()");
    }
}
