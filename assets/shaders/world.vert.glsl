// The map's walls, floor and ceiling: one static mesh per texture, drawn by WorldRenderer.
// a_shade is baked per vertex by MapGeometry (SHADE_NORTH_SOUTH 1.0, SHADE_EAST_WEST 0.72, SHADE_FLOOR 0.9,
// SHADE_CEILING 0.62); model.vert.glsl and viewmodel.vert.glsl repeat those four numbers and must change with them.
// The uniform and attribute names are the contract with WorldRenderer; ShaderFilesTest checks them.
attribute vec3 a_position;
attribute vec2 a_texCoord0;
attribute float a_shade;
uniform mat4 u_projViewTrans;
uniform vec3 u_cameraPosition;
uniform vec2 u_fogRange;
varying vec2 v_texCoord;
varying float v_shade;
varying float v_fog;
void main() {
    v_texCoord = a_texCoord0;
    v_shade = a_shade;
    float range = length(a_position - u_cameraPosition);
    v_fog = clamp((range - u_fogRange.x) / (u_fogRange.y - u_fogRange.x), 0.0, 1.0);
    gl_Position = u_projViewTrans * vec4(a_position, 1.0);
}
