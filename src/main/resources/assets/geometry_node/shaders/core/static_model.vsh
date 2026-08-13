#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
#ifdef HAS_SKIN
in vec4 Joints;
in vec4 Weights;
layout(std140) uniform SkinPalette {
    mat4 JointMatrices[128];
    mat4 JointNormalMatrices[128];
};
#endif
#ifdef HAS_NORMAL
in vec3 Normal;
out vec3 normalView;
#endif
#ifdef HAS_UV
in vec2 UV0;
out vec2 texCoord0;
#endif
#ifdef HAS_COLOR
in vec4 Color;
#endif

out vec4 vertexColor;

void main() {
    mat4 skin = mat4(1.0);
#ifdef HAS_SKIN
    skin = JointMatrices[int(Joints.x)] * Weights.x
         + JointMatrices[int(Joints.y)] * Weights.y
         + JointMatrices[int(Joints.z)] * Weights.z
         + JointMatrices[int(Joints.w)] * Weights.w;
#endif
    gl_Position = ProjMat * ModelViewMat * skin * vec4(Position, 1.0);
    vec4 base = ColorModulator;
#ifdef HAS_COLOR
    base *= Color;
#endif
#ifdef HAS_NORMAL
#ifdef HAS_SKIN
    mat3 skinNormal = mat3(JointNormalMatrices[int(Joints.x)]) * Weights.x
                    + mat3(JointNormalMatrices[int(Joints.y)]) * Weights.y
                    + mat3(JointNormalMatrices[int(Joints.z)]) * Weights.z
                    + mat3(JointNormalMatrices[int(Joints.w)]) * Weights.w;
    normalView = normalize(transpose(inverse(mat3(ModelViewMat))) * skinNormal * Normal);
#else
    normalView = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
#endif
#endif
    vertexColor = base;
#ifdef HAS_UV
    texCoord0 = UV0;
#endif
}
